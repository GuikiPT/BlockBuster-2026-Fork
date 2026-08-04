package mchorse.blockbuster.client.aperture;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

import mchorse.aperture.camera.ModifierRegistry;
import mchorse.aperture.client.gui.GuiModifiersManager;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.aperture.TrackerModifierWiring;
import mchorse.blockbuster.aperture.camera.modifiers.TrackerModifier;
import mchorse.blockbuster.aperture.gui.panels.modifiers.GuiTrackerModifierPanel;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.blockbuster_pack.client.render.TrackerMorphRenderer;
import mchorse.blockbuster_pack.trackers.ApertureCamera;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.Color;
import mchorse.metamorph.bodypart.BodyPartRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * S22 <b>P240</b> — the client half of Blockbuster's tracker camera modifier.
 *
 * <p>Four things, all of which legacy did from client-side Blockbuster code:</p>
 * <ol>
 *   <li><b>Timeline colour + panel.</b> Legacy
 *       {@code CameraHandler.registerClientModifiers()}:
 *       {@code registerClient(TrackerModifier.class,
 *       "blockbuster.gui.aperture.modifiers.tracker", new Color(0.5F, 0.5F,
 *       0.5F))} plus the {@code GuiModifiersManager.PANELS} entry. These are
 *       <b>not optional</b>: {@code GuiModifiersManager} walks
 *       {@code 0 .. ModifierRegistry.getNextId()} and dereferences the client
 *       info for every id, so registering the modifier without its client info
 *       would NPE the "add modifier" popup. That is why {@link #install()}
 *       calls {@link TrackerModifierWiring#install()} itself — the two halves
 *       are one atomic step, in any call order.</li>
 *   <li><b>{@link TrackerModifier#actorQuery}</b> — legacy
 *       {@code TrackerModifier.queryActor}: the tracker's selector is a
 *       <i>tracker-morph name</i> scoped to the currently open scene, not an
 *       entity selector, so it cannot reuse
 *       {@code EntityModifier.clientEntityFinder}.</li>
 *   <li><b>{@link ApertureCamera#captureHook}</b> — the core-profile
 *       replacement for legacy's {@code GL_MODELVIEW} readback. Without it
 *       {@code ApertureCamera.track} returned immediately and camera-follow
 *       tracking was dead even once the modifier existed.</li>
 * </ol>
 *
 * <h2>The capture, in detail (rewritten by S22 P285)</h2>
 * <p>Legacy's tracker modifier pushed an <b>identity</b> model-view and
 * re-rendered the tracked entity at its absolute world position, so the matrix
 * {@code ApertureCamera} scraped was a plain world matrix, captured
 * synchronously on the modifier's own call stack. {@link #capturePass} is that,
 * in core profile: a fresh {@link MatrixStack} (already identity) translated to
 * the entity's interpolated world position, handed to the entity's own
 * {@link net.minecraft.client.render.entity.EntityRenderer} together with
 * {@link mchorse.metamorph.bodypart.BodyPartRenderer#DISCARD} — the same
 * no-draw sink {@code BodyPartRenderer.recordMatrix} already uses to walk a
 * model for its matrices without painting anything. Going through the real
 * renderer rather than re-deriving the transform chain is the whole point: the
 * pose pass, the body-part limb walk, the morph-transition transform and every
 * mixin on the path are by construction the ones the visible frame will use a
 * few hundred microseconds later.</p>
 *
 * <p>So {@link #worldMatrix} no longer reconstructs anything — the stack it is
 * handed <b>is</b> the world matrix, and all that is left is legacy's offset
 * block ({@code translate(offsetPos)}, then {@code -yaw} about Y,
 * {@code pitch} about X, {@code roll} about Z), post-multiplied exactly as the
 * {@code glPushMatrix … glPopMatrix} block inside {@code ApertureCamera.track}
 * did it. Float precision is the same as legacy's — {@code GL_MODELVIEW} was a
 * float matrix fed absolute world coordinates there too, and
 * {@code ApertureCamera.pos} is a {@code javax.vecmath.Vector3f} regardless.</p>
 *
 * <h2>Why P240's capture had to go, and what P274 taught us</h2>
 * <p>P240 captured during the <b>normal render pass</b>, where the stack is
 * camera-space, and reconstructed the world matrix with
 * {@code T(p_cam) · R_view⁻¹}. Two things were wrong with that:</p>
 * <ol>
 *   <li><b>P274:</b> {@code R_view⁻¹} is <i>not</i>
 *       {@code Camera.getRotation()}. {@code GameRenderer.renderWorld} builds
 *       {@code R_view = Rx(pitch) · Ry(yaw + 180)} while
 *       {@code Camera.setRotation} stores {@code Ry(-yaw) · Rx(pitch)}; their
 *       product is {@code Ry(180)}, not identity. Vanilla gets away with using
 *       {@code getRotation()} for billboards, where a half turn is invisible; a
 *       <i>position</i> reconstruction reported the tracker <b>reflected
 *       through the camera</b>. P274 fixed it with a trailing
 *       {@code rotateY(180°)}. P285 deletes the reconstruction outright, so the
 *       trap is gone rather than patched — the fact itself is kept alive as a
 *       standing assertion in {@code TrackerModifierClientWiringTest} so a
 *       future camera-space capture cannot walk into it again.</li>
 *   <li><b>P285 (the user-reported bug):</b> the modifier runs from
 *       {@code Camera.update} and the entity pass runs later in the same
 *       {@code GameRenderer.renderWorld}, so the pose consumed was always the
 *       <b>previous</b> frame's. The camera trailed the tracked model and shook
 *       whenever its velocity changed. Off-screen (frustum-culled) actors never
 *       published at all, and a GUI morph preview could win the one-shot arm and
 *       publish a matrix from a stack that is not camera-space in the first
 *       place.</li>
 * </ol>
 *
 * <p>One {@code install()}, one line in {@code ApertureClient} (S22 shared-file
 * protocol).</p>
 *
 * Legacy sources:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/CameraHandler.java
 * ({@code registerClientModifiers}),
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/camera/modifiers/TrackerModifier.java
 * ({@code queryActor}),
 * blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/trackers/ApertureCamera.java
 */
public final class TrackerModifierClientWiring
{
    /** Legacy timeline colour for the tracker modifier. */
    public static final Color TRACKER_COLOR = new Color(0.5F, 0.5F, 0.5F);

    /** Legacy title lang key. */
    public static final String TRACKER_TITLE = "blockbuster.gui.aperture.modifiers.tracker";

    private TrackerModifierClientWiring()
    {}

    /** Install the P240 client half. Idempotent. */
    public static void install()
    {
        /* The modifier must exist before its client info can be attached, and
         * the two must never be observable apart (see class javadoc). */
        TrackerModifierWiring.install();

        ModifierRegistry.registerClient(TrackerModifier.class, TRACKER_TITLE, TRACKER_COLOR);
        GuiModifiersManager.PANELS.put(TrackerModifier.class, GuiTrackerModifierPanel.class);

        TrackerModifier.actorQuery = TrackerModifierClientWiring::queryActor;
        TrackerModifier.capturePass = TrackerModifierClientWiring::capturePass;
        ApertureCamera.captureHook = TrackerModifierClientWiring::capture;
    }

    /* --------------------------------------------------------------------- */
    /* Actor query (legacy TrackerModifier.queryActor)                       */
    /* --------------------------------------------------------------------- */

    /**
     * Every living entity in the client world that (a) is alive, (b) is playing
     * back a record belonging to the open scene panel's replay list, and (c)
     * carries a {@code TrackerMorph} with an {@code ApertureCamera} tracker
     * named {@code selector} anywhere in its morph tree (body parts included).
     *
     * <p>Legacy gated the whole query on {@code CameraHandler.get() != null}
     * (no attached scene ⇒ nothing to track) and dereferenced the scene panel
     * unconditionally; the port keeps the gate and adds the same null guard
     * {@code CameraHandlerClient.onCameraRewind} needed, because
     * {@code getReplays()} is null for a location that was never opened.</p>
     */
    public static List<Entity> queryActor(String selector)
    {
        if (CameraHandler.get() == null)
        {
            return null;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        GuiBlockbusterPanels panels = BlockbusterClient.panels;

        if (mc == null || mc.world == null || panels == null || panels.scenePanel == null)
        {
            return null;
        }

        List<Replay> sceneReplays = panels.scenePanel.getReplays();

        if (sceneReplays == null)
        {
            return null;
        }

        List<String> replays = new ArrayList<String>();

        for (Replay replay : sceneReplays)
        {
            replays.add(replay.id);
        }

        List<Entity> entities = new ArrayList<Entity>();

        for (Entity entity : mc.world.getEntities())
        {
            if (!(entity instanceof LivingEntity actor) || !actor.isAlive())
            {
                continue;
            }

            RecordPlayer player = EntityUtils.getRecordPlayer(actor);

            if (player == null || player.record == null || !replays.contains(player.record.filename))
            {
                continue;
            }

            if (TrackerModifier.checkTracker(mchorse.metamorph.api.EntityUtils.getMorph(actor), selector))
            {
                entities.add(actor);
            }
        }

        return entities.isEmpty() ? null : entities;
    }

    /* --------------------------------------------------------------------- */
    /* Matrix capture (legacy ApertureCamera.track's GL block)               */
    /* --------------------------------------------------------------------- */

    /** One-shot latch so a capture pass that throws cannot spam the log. */
    private static boolean reportedCaptureFailure;

    /**
     * Legacy's forced off-screen re-render (S22 P285), core-profile edition:
     * draw {@code entity} into a throwaway identity {@link MatrixStack} at its
     * interpolated world position, with every vertex discarded, so the
     * {@code TrackerMorph} somewhere in its morph tree runs
     * {@code ApertureCamera.track} against a plain <b>world</b> matrix — right
     * now, on the caller's stack, at the caller's {@code partialTick}.
     *
     * <p>The position is vanilla's own: {@code WorldRenderer.renderEntity}
     * lerps {@code lastRenderX/Y/Z} (<i>not</i> {@code prevX/Y/Z}) and
     * {@code prevYaw}, and adds the renderer's {@code getPositionOffset}. That
     * is the same triple legacy computed from {@code lastTickPos*}, which was
     * 1.12.2's name for exactly this field, and it is what puts the capture on
     * the pixel the model will be drawn on.</p>
     *
     * <p>Coordinates are absolute rather than camera-relative, again as legacy:
     * the camera is mid-{@code update()} when this runs, so its position is not
     * a stable origin to hang the capture off, and {@code ApertureCamera.pos}
     * is a float vector either way.</p>
     *
     * <p>Light is {@link MorphRenderContext#FULL_BRIGHT} rather than the
     * dispatcher's sample: nothing is being lit, and
     * {@code EntityRenderDispatcher.getLight} would touch the world for a value
     * that cannot reach the matrix.</p>
     */
    public static void capturePass(Entity entity, float partialTick)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || entity == null || ApertureCamera.capturing)
        {
            return;
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        @SuppressWarnings("rawtypes")
        EntityRenderer renderer = dispatcher == null ? null : dispatcher.getRenderer(entity);

        if (renderer == null)
        {
            return;
        }

        Vec3d offset = renderer.getPositionOffset(entity, partialTick);
        double x = MathHelper.lerp((double) partialTick, entity.lastRenderX, entity.getX()) + offset.x;
        double y = MathHelper.lerp((double) partialTick, entity.lastRenderY, entity.getY()) + offset.y;
        double z = MathHelper.lerp((double) partialTick, entity.lastRenderZ, entity.getZ()) + offset.z;
        float yaw = MathHelper.lerp(partialTick, entity.prevYaw, entity.getYaw());

        MatrixStack matrices = new MatrixStack();

        matrices.translate(x, y, z);

        ApertureCamera.capturing = true;

        try
        {
            renderer.render(entity, yaw, partialTick, matrices, BodyPartRenderer.DISCARD, MorphRenderContext.FULL_BRIGHT);
        }
        catch (Exception e)
        {
            /* Totality: a throwaway pass that throws must not take the camera —
             * or the frame — down with it. Logged once: this runs every frame,
             * and whatever broke will break identically on the next one. */
            if (!reportedCaptureFailure)
            {
                reportedCaptureFailure = true;

                Blockbuster.LOGGER.error("Tracker capture pass failed for " + entity, e);
            }
        }
        finally
        {
            ApertureCamera.capturing = false;
        }
    }

    /**
     * Fill {@code buffer} with the offset-applied <b>world</b> model-view of
     * the tracker render currently in progress. Outside the synchronous capture
     * pass (or headless) the buffer is left identity, which makes
     * {@code ApertureCamera.extract} publish the origin rather than garbage.
     *
     * <p>The {@link ApertureCamera#capturing} gate is what makes "the pose the
     * modifier consumes is this frame's" structural rather than incidental: the
     * only stack this will ever read is the one {@link #capturePass} built four
     * stack frames up. A tracker morph drawn by the world pass, by a GUI
     * preview or by a model-editor viewport reaches
     * {@code ApertureCamera.track} too, but its {@code enable} guard is down
     * outside the pass, and if it somehow were not, this returns identity
     * rather than publishing a camera-space or GUI-space matrix as a world
     * one.</p>
     */
    public static void capture(javax.vecmath.Matrix4f buffer)
    {
        MatrixStack matrices = TrackerMorphRenderer.currentMatrices();

        if (matrices == null || !ApertureCamera.capturing)
        {
            buffer.setIdentity();

            return;
        }

        buffer.set(RenderingUtilsClient.toVecmath(worldMatrix(matrices.peek().getPositionMatrix())));
    }

    /**
     * Legacy's offset block on top of the captured world model-view. Pure and
     * public so the matrix algebra is testable without a client.
     *
     * <p>There is no camera term and no view rotation to undo: {@link
     * #capturePass} renders from identity at absolute world coordinates, so
     * {@code modelView} already <i>is</i> {@code T(p_tracked) · model} — the
     * matrix 1.12.2 read back out of an identity-loaded {@code GL_MODELVIEW}
     * (S22 P285; see the class javadoc for the camera-space reconstruction this
     * replaces and the P274 trap that lived in it).</p>
     */
    public static Matrix4f worldMatrix(Matrix4f modelView)
    {
        Matrix4f world = new Matrix4f(modelView);

        /* Legacy: glTranslated(offsetPos); glRotatef(-offsetRot.y, 0,1,0);
         * glRotatef(offsetRot.x, 1,0,0); glRotatef(offsetRot.z, 0,0,1) */
        world.translate(ApertureCamera.offsetPos.x, ApertureCamera.offsetPos.y, ApertureCamera.offsetPos.z);
        world.rotateY((float) Math.toRadians(-ApertureCamera.offsetRot.y));
        world.rotateX((float) Math.toRadians(ApertureCamera.offsetRot.x));
        world.rotateZ((float) Math.toRadians(ApertureCamera.offsetRot.z));

        return world;
    }
}
