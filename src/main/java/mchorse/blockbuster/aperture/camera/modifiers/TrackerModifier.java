package mchorse.blockbuster.aperture.camera.modifiers;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.data.Angle;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.aperture.camera.modifiers.AbstractModifier;
import mchorse.aperture.camera.modifiers.EntityModifier;
import mchorse.blockbuster_pack.morphs.TrackerMorph;
import mchorse.blockbuster_pack.trackers.ApertureCamera;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueFloat;
import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Blockbuster's <b>tracker</b> camera modifier (roadmap P176, landed S22
 * P240) — byte id <b>10</b>, string id {@code "tracker"} in Aperture's
 * {@code ModifierRegistry}.
 *
 * <h2>Why this class had to land</h2>
 * <p>Until S22 the id was reserved but unregistered, which made
 * {@code ModifierSerializer.fromJSON} return {@code null} for
 * {@code "type": "tracker"} and {@code ValueModifiers} silently drop it:
 * <b>a legacy camera profile containing a tracker modifier lost it on load,
 * and the next save wrote the loss to disk.</b> The wire format was safe (the
 * byte id was reserved by the deferral); the user's data was not.</p>
 *
 * <h2>Serialization contract</h2>
 * <p>Registered value keys are the saved-profile JSON contract and are
 * snake_case where the Java field is camelCase: {@code yaw}, {@code pitch},
 * {@code roll}, {@code relative} (default <b>true</b>), {@code main_cam}
 * (default <b>true</b>), {@code look_at} (default false), plus
 * {@code selector} / {@code offset} inherited from {@link EntityModifier} and
 * {@code enabled} / {@code envelope} from {@link AbstractModifier}. Do not
 * rename them.</p>
 *
 * <h2>Capture mechanism — synchronous, as 1.12.2 (S22 P285)</h2>
 * <p>Legacy pushed an identity {@code GL_MODELVIEW}, set
 * {@code ApertureCamera.enable = true}, re-rendered the tracked entity
 * off-screen with {@code render.doRender(...)} so its {@code TrackerMorph}
 * would scrape the model-view back out, then popped and called
 * {@code GlStateManager.disableLighting()}. The capture therefore happened on
 * <b>this</b> call stack, a dozen lines before the values are read.</p>
 *
 * <p>P240 replaced that with a capture during the normal render pass, which
 * S15 open question 4 signed off. That was wrong in a way the user feels: the
 * modifier runs from {@code Camera.update} (bytecode offset 176 of
 * {@code GameRenderer.renderWorld}) and the entity pass runs from
 * {@code WorldRenderer.render} (offset 619), so the values consumed were the
 * ones published <b>one frame earlier</b> — the camera trailed the model and
 * shook whenever its velocity changed. It also let the arm be won by a GUI
 * morph preview, and left the camera frozen on its last value whenever the
 * tracked actor was frustum-culled.</p>
 *
 * <p><b>P285 restores the legacy shape:</b> {@link #capturePass} is a client
 * seam that re-renders the tracked entity into a fresh, identity
 * {@link net.minecraft.client.util.math.MatrixStack} at its interpolated world
 * position with a discarding vertex sink — no draw calls, no GL — while
 * {@code ApertureCamera.enable} is up, and the flag is lowered again
 * immediately after. So {@link ApertureCamera#pos}/{@link ApertureCamera#rot}
 * are written by the render on line <i>n</i> and read on line <i>n+4</i> of
 * this very method, at the same {@code partialTick}. Everything else — the
 * {@code relative} / {@code mainCam} / {@code lookAt} branches — is the legacy
 * body verbatim, <b>including</b> the quirk that {@code lookAt} mode never
 * assigns {@link ApertureCamera#tracking} or the offsets.</p>
 *
 * <p>{@link #tryFindingEntity()} is a scene-aware query, not a plain entity
 * selector, so it goes through {@link #actorQuery} (installed by
 * {@code mchorse.blockbuster.client.aperture.TrackerModifierClientWiring});
 * headless it stays {@code null} and the modifier is inert, exactly like every
 * other {@link EntityModifier} without a client.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/camera/modifiers/TrackerModifier.java
 */
public class TrackerModifier extends EntityModifier
{
    /**
     * Client seam for legacy {@code queryActor(selector)} — the scene-scoped
     * actor lookup: every living entity in the client world whose
     * {@code RecordPlayer}'s record filename appears in the <b>currently open
     * scene panel's</b> replay list and whose morph tree carries a
     * {@link TrackerMorph} with an {@link ApertureCamera} tracker of the given
     * name. Null (headless / dedicated server) ⇒ never resolves.
     */
    public static Function<String, List<Entity>> actorQuery;

    /**
     * Client seam for legacy's forced off-screen re-render (S22 P285).
     *
     * <p>1.12.2 inlined {@code glPushMatrix(); glLoadIdentity();
     * render.doRender(entity, baseX, baseY, baseZ, yaw, partialTick);
     * glPopMatrix();} right here. The 1.20.4 equivalent needs a
     * {@code MatrixStack} and a {@code VertexConsumerProvider}, both of which
     * are client-only types, so it lives in
     * {@code TrackerModifierClientWiring.capturePass}. Null headless and on a
     * dedicated server, in which case the modifier consumes whatever
     * {@link ApertureCamera#pos}/{@link ApertureCamera#rot} already hold —
     * which, with no client, is nothing at all.</p>
     */
    public interface ICapturePass
    {
        void capture(Entity entity, float partialTick);
    }

    public static ICapturePass capturePass;

    public final ValueFloat yaw = new ValueFloat("yaw");
    public final ValueFloat pitch = new ValueFloat("pitch");
    public final ValueFloat roll = new ValueFloat("roll");
    public final ValueBoolean relative = new ValueBoolean("relative", true);
    public final ValueBoolean mainCam = new ValueBoolean("main_cam", true);
    public final ValueBoolean lookAt = new ValueBoolean("look_at");

    public TrackerModifier()
    {
        super();

        this.register(this.yaw);
        this.register(this.pitch);
        this.register(this.roll);
        this.register(this.relative);
        this.register(this.mainCam);
        this.register(this.lookAt);
    }

    @Override
    public AbstractModifier create()
    {
        return new TrackerModifier();
    }

    @Override
    public void modify(long ticks, long offset, AbstractFixture fixture, float partialTick, float previewPartialTick, CameraProfile profile, Position pos)
    {
        if (this.checkForDead())
        {
            this.tryFindingEntity();
        }

        if (this.entities == null)
        {
            return;
        }

        if (!this.lookAt.get())
        {
            this.position.copy(pos);
        }

        if (fixture != null && this.relative.get())
        {
            fixture.applyFixture(0, 0, 0, profile, this.position);
        }

        if (!this.lookAt.get())
        {
            /* Legacy comment: "probably unnecessary, the other modifiers also
             * dont have this" */
            this.position.point.x = pos.point.x - this.position.point.x;
            this.position.point.y = pos.point.y - this.position.point.y;
            this.position.point.z = pos.point.z - this.position.point.z;

            this.position.angle.yaw = pos.angle.yaw - this.position.angle.yaw;
            this.position.angle.pitch = pos.angle.pitch - this.position.angle.pitch;
            this.position.angle.roll = pos.angle.roll - this.position.angle.roll;

            /* Legacy TODO: "refactor this. Get rid of static buffer variables,
             * I dont know why they exist" — they are the ApertureCamera
             * tracker's API and stay statics (see S14 P166). */
            ApertureCamera.tracking = this.selector.get();

            ApertureCamera.offsetPos.x = (float) this.offset.get().x;
            ApertureCamera.offsetPos.y = (float) this.offset.get().y;
            ApertureCamera.offsetPos.z = (float) this.offset.get().z;

            ApertureCamera.offsetRot.x = this.pitch.get();
            ApertureCamera.offsetRot.y = this.yaw.get();
            ApertureCamera.offsetRot.z = this.roll.get();

            if (this.mainCam.get())
            {
                ApertureCamera.offsetPos.x += this.position.point.x;
                ApertureCamera.offsetPos.y += this.position.point.y;
                ApertureCamera.offsetPos.z += this.position.point.z;

                ApertureCamera.offsetRot.x += this.position.angle.pitch;
                ApertureCamera.offsetRot.y += this.position.angle.yaw;
                ApertureCamera.offsetRot.z += this.position.angle.roll;
            }
        }

        /* Legacy: glPushMatrix(); glLoadIdentity(); enable = true;
         * render.doRender(entity, baseX, baseY, baseZ, yaw, partialTick);
         * enable = false; glPopMatrix(); — the tracked morph publishes
         * ApertureCamera.pos/rot from inside that call, so the reads below are
         * of THIS frame's pose (S22 P285). The arm is lowered again whether or
         * not the pass ran, exactly as legacy did, so the normal world render
         * can never capture. */
        Entity tracked = this.entities.get(0);

        ApertureCamera.enable = true;

        try
        {
            if (capturePass != null)
            {
                capturePass.capture(tracked, partialTick);
            }
        }
        finally
        {
            ApertureCamera.enable = false;
        }

        if (this.lookAt.get())
        {
            double dX = ApertureCamera.pos.x - pos.point.x + this.offset.get().x;
            double dY = ApertureCamera.pos.y - pos.point.y + this.offset.get().y;
            double dZ = ApertureCamera.pos.z - pos.point.z + this.offset.get().z;

            Angle angle = Angle.angle(dX, dY, dZ);

            if (this.relative.get())
            {
                angle.yaw += pos.angle.yaw + this.yaw.get() - this.position.angle.yaw;
                angle.pitch += pos.angle.pitch + this.pitch.get() - this.position.angle.pitch;
            }

            pos.angle.set(angle.yaw, angle.pitch);
        }
        else
        {
            pos.point.set(ApertureCamera.pos.x, ApertureCamera.pos.y, ApertureCamera.pos.z);

            if (this.mainCam.get())
            {
                pos.angle.set(ApertureCamera.rot.y, ApertureCamera.rot.x, ApertureCamera.rot.z, pos.angle.fov);
            }
            else
            {
                pos.point.x += this.position.point.x;
                pos.point.y += this.position.point.y;
                pos.point.z += this.position.point.z;
            }
        }
    }

    @Override
    public void tryFindingEntity()
    {
        String selector = this.selector.get();

        this.entities = null;

        if (selector == null || selector.isEmpty() || actorQuery == null)
        {
            return;
        }

        List<Entity> entities = actorQuery.apply(selector);

        if (entities != null && !entities.isEmpty())
        {
            this.entities = entities;
        }
    }

    /**
     * Legacy override: on top of the inherited dead/local-player pruning, drop
     * every entity whose morph tree <b>no longer contains</b> the named tracker
     * — re-morphing an actor detaches the camera mid-shot, and scenes rely on
     * that.
     */
    @Override
    protected boolean checkForDead()
    {
        if (!super.checkForDead())
        {
            Iterator<Entity> it = this.entities.iterator();

            while (it.hasNext())
            {
                Entity entity = it.next();
                AbstractMorph morph = entity instanceof LivingEntity living
                    ? EntityUtils.getMorph(living)
                    : null;

                if (!checkTracker(morph, this.selector.get()))
                {
                    it.remove();
                }
            }

            if (this.entities.isEmpty())
            {
                this.entities = null;
            }
        }

        return this.entities == null;
    }

    /**
     * Legacy {@code checkTracker} — does this morph tree (body parts included,
     * via {@code MorphUtils.anyMatch}) carry a {@link TrackerMorph} whose
     * tracker is an {@link ApertureCamera} named {@code selector}? Public and
     * static so the client-side actor query shares exactly this predicate.
     */
    public static boolean checkTracker(AbstractMorph morph, String selector)
    {
        if (morph == null || selector == null)
        {
            return false;
        }

        return MorphUtils.anyMatch(morph, (element) -> element instanceof TrackerMorph
            && ((TrackerMorph) element).tracker instanceof ApertureCamera
            && selector.equals(((TrackerMorph) element).tracker.name));
    }
}
