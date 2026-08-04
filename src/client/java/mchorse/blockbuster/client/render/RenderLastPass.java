package mchorse.blockbuster.client.render;

import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.mclib.utils.OptifineHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Installs the render-last tail pass (roadmap P80.3) — the injection point, the
 * draw dispatch and the sweep's world query.
 *
 * <p>{@link RenderingHandler} owns the queue, the guard and the sort; this class
 * owns everything that needs a live client. Split that way so the whole
 * lifecycle stays headlessly assertable: nothing in {@code RenderingHandler}
 * touches {@code MinecraftClient}, and everything here is null-total so the
 * registration itself can be exercised in a test JVM with no client at all.</p>
 *
 * <h1>The injection point: {@code WorldRenderEvents.BEFORE_DEBUG_RENDER}</h1>
 *
 * <p>This answers S06 open question 6 ("the exact {@code WorldRenderer.render}
 * ordinal that corresponds to 1.12.2's <i>after {@code postRenderDamagedBlocks}</i>").
 * Verified against {@code fabric-rendering-v1 3.0.11+9468a19d32} and against the
 * 1.20.4 {@code WorldRenderer.render} bytecode (javap on the loom-cache named
 * jar), not from memory:</p>
 *
 * <p>Fabric injects this event at
 * {@code INVOKE DebugRenderer.render(...) ordinal 0}. In {@code WorldRenderer
 * .render} that call sits <b>after</b> the entity loop, <b>after</b> the block
 * entity loop, <b>after</b> the solid/cutout entity-layer flush, <b>after</b>
 * the entity-outline post effect, <b>after</b> {@code BlockRenderManager
 * .renderDamage} (the block-breaking crack overlay) and after the block outline
 * — and <b>before</b> the debug renderers, before the translucent entity-layer
 * flush ({@code getEntityTranslucentCull}, the glints, the banner/shield
 * patterns), before translucent terrain, and before particles, clouds and
 * weather.</p>
 *
 * <p>"After the block-breaking overlay, before translucency" is precisely
 * 1.12.2's chosen point — legacy's own transformer comment says it inserted
 * after {@code postRenderDamagedBlocks} "to avoid OpenGL states that were meant
 * for damagedBlocks". So this is a positional match, not an approximation.</p>
 *
 * <p>Two further properties decided it over the alternatives:</p>
 * <ul>
 *   <li><b>{@code consumers()} is non-null here.</b> {@code WorldRenderContext}
 *   documents it as null before {@code BEFORE_ENTITIES} and after
 *   {@code BEFORE_DEBUG_RENDER} — this event is the <i>last</i> one that still
 *   has a real {@code VertexConsumerProvider}. {@code AFTER_TRANSLUCENT} and
 *   {@code LAST} would force raw framebuffer draws, and every morph renderer in
 *   the port emits into a {@code VertexConsumer}.</li>
 *   <li><b>Our quads still get flushed in the right batch.</b> Because the
 *   translucent entity layers are drawn <i>after</i> this point, morphs we emit
 *   here land in the same {@code Immediate} buffers vanilla is about to flush —
 *   and within one {@code RenderLayer} a buffer draws in submission order, which
 *   is exactly what makes the back-to-front sort visible. Injecting after the
 *   flush would have made the sort inert.</li>
 * </ul>
 *
 * <p><b>Not {@code AFTER_ENTITIES}</b>: its javadoc says it fires "after entities
 * are rendered … <i>before block entity rendering begins</i>", so model blocks
 * would not have enqueued yet — the pass would drain a full actor queue against
 * an empty block-entity queue and the two would never sort against each other.</p>
 *
 * <p><b>Not a TAIL injection on {@code blockbuster/mixin/client/WorldRendererMixin}</b>
 * (the P203 chroma-sky mixin), which was the other candidate: Sodium replaces
 * {@code WorldRenderer}'s internals but keeps Fabric's events working, while
 * arbitrary third-party injections into {@code WorldRenderer} are exactly what it
 * does not keep working — and S06's own note already says "TAIL injections only"
 * for that reason. The event is the lower-risk seam and needs no mixin at all.</p>
 *
 * <h1>The sort origin: camera eye, deliberately</h1>
 *
 * <p>Legacy measured with {@code Entity.getDistanceSq} from
 * {@code mc.getRenderViewEntity()} — the render-view entity's <b>feet</b>. On
 * 1.20.4 {@code camera.getPos()} is the <b>eye</b>, about 1.62 blocks higher, and
 * this class uses the eye. The choice is deliberate, not inherited:</p>
 * <ul>
 *   <li>Painter's-order correctness wants distance from the <i>projection
 *   centre</i>, which is the camera, not the feet. Legacy used the feet because
 *   {@code getDistanceSq} was the convenient method on {@code Entity}, and its
 *   result is measurably worse: two model blocks at eye height and at foot
 *   height sort by a key offset from the actual view point.</li>
 *   <li>The eye is also the only origin that is correct in the cases legacy could
 *   not express at all — free camera, Aperture camera playback, spectator — where
 *   there may be no focused entity whose feet to measure from.</li>
 *   <li>The visible difference is confined to objects within roughly 1.6 blocks
 *   of each other in depth; anything further apart sorts identically either way.
 *   {@code camera.getFocusedEntity()} is the strict-parity alternative if the
 *   eyeball checklist ever shows a scene composed against the feet ordering.</li>
 * </ul>
 */
public final class RenderLastPass
{
    private static boolean installed;

    private RenderLastPass()
    {}

    /**
     * Register the tail pass and fill {@link RenderingHandler}'s two client
     * seams.
     *
     * <p>The seams are (re)assigned on every call; only the event registration
     * is guarded, because Fabric events cannot be unregistered and a second
     * registration would drain the queue twice per frame — the first drain would
     * empty it and the second would draw nothing, silently halving nothing but
     * costing a sort. Splitting it this way also means a caller can always get
     * the seams back to their production values.</p>
     */
    public static synchronized void install()
    {
        RenderingHandler.renderLastDrawer = RenderLastPass::draw;
        RenderingHandler.aliveActorSupplier = RenderLastPass::aliveActors;

        if (installed)
        {
            return;
        }

        installed = true;

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(RenderLastPass::onBeforeDebugRender);
    }

    /** Whether {@link #install()} has run (registration pin). */
    public static boolean isInstalled()
    {
        return installed;
    }

    /**
     * The drain, bound to the event. Total: a context with no camera sorts from
     * the origin and a context with no consumers still drains, because the one
     * thing that must happen every frame is that the queue is emptied.
     */
    public static void onBeforeDebugRender(WorldRenderContext context)
    {
        Camera camera = context.camera();
        Vec3d origin = camera == null ? Vec3d.ZERO : camera.getPos();

        RenderingHandler.drawRenderLast(context.matrixStack(), context.consumers(),
            origin.x, origin.y, origin.z, context.tickDelta());
    }

    /**
     * Legacy's two-branch dispatch (1.12.2 lines 379-401), including the two
     * Optifine per-object notifications, which are documented no-ops on Iris
     * (see {@link OptifineHelper#nextEntity}).
     */
    public static void draw(IRenderLast item, MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || consumers == null || matrices == null)
        {
            return;
        }

        if (item instanceof EntityActor actor)
        {
            OptifineHelper.nextEntity(actor);

            drawEntity(mc, actor, matrices, consumers, originX, originY, originZ, tickDelta);
        }
        else if (item instanceof TileEntityModel te)
        {
            OptifineHelper.nextBlockEntity(te);

            drawBlockEntity(mc, te, matrices, consumers, originX, originY, originZ, tickDelta);
        }
    }

    /**
     * Legacy {@code mc.getRenderManager().renderEntityStatic(actor,
     * partialTicks, false)}. On 1.20.4 the equivalent is vanilla's own private
     * {@code WorldRenderer.renderEntity}, reproduced here verbatim (verified
     * against the {@code WorldRenderer} bytecode): the draw lerps
     * {@code lastRenderX/Y/Z} and {@code prevYaw} — <b>not</b> the
     * {@code prevX/Y/Z} the sort key uses — and hands the dispatcher
     * camera-relative coordinates plus {@code getLight(entity, tickDelta)}.
     *
     * <p>No {@code push}/{@code pop} here: the dispatcher does its own, and
     * {@code drawRenderLast} already brackets every item.</p>
     */
    private static void drawEntity(MinecraftClient mc, EntityActor actor, MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta)
    {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        double x = MathHelper.lerp((double) tickDelta, actor.lastRenderX, actor.getX());
        double y = MathHelper.lerp((double) tickDelta, actor.lastRenderY, actor.getY());
        double z = MathHelper.lerp((double) tickDelta, actor.lastRenderZ, actor.getZ());
        float yaw = MathHelper.lerp(tickDelta, actor.prevYaw, actor.getYaw());

        dispatcher.render(actor, x - originX, y - originY, z - originZ, yaw, tickDelta,
            matrices, consumers, dispatcher.getLight(actor, tickDelta));
    }

    /**
     * Legacy {@code TileEntityRendererDispatcher.instance.render(te,
     * partialTicks, -1)}. 1.20.4's {@code BlockEntityRenderDispatcher.render}
     * expects the matrix already translated to the block's corner, which in the
     * normal pass {@code WorldRenderer} does for it — so the tail pass does it
     * here, camera-relative as {@code WorldRenderContext} requires.
     *
     * <p>The legacy {@code -1} destroy-stage argument has no 1.20.4 analogue;
     * the crack overlay is applied by the caller through an
     * {@code OverlayVertexConsumer}, and a deferred block entity is past that
     * point in the frame — so a render-last model block shows no breaking
     * cracks. Checklist row E23.3.</p>
     */
    private static void drawBlockEntity(MinecraftClient mc, TileEntityModel te, MatrixStack matrices, VertexConsumerProvider consumers, double originX, double originY, double originZ, float tickDelta)
    {
        BlockPos pos = te.getPos();

        matrices.translate(pos.getX() - originX, pos.getY() - originY, pos.getZ() - originZ);

        mc.getBlockEntityRenderDispatcher().render(te, tickDelta, matrices, consumers);
    }

    /**
     * Legacy {@code mc.world.getEntities(EntityActor.class,
     * EntitySelectors.IS_ALIVE)} for the {@code actorAlwaysRender} sweep.
     */
    private static List<EntityActor> aliveActors()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc == null ? null : mc.world;

        List<EntityActor> actors = new ArrayList<EntityActor>();

        if (world == null)
        {
            return actors;
        }

        for (Entity entity : world.getEntities())
        {
            if (entity instanceof EntityActor actor && actor.isAlive())
            {
                actors.add(actor);
            }
        }

        return actors;
    }
}
