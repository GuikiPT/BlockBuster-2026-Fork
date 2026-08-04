package mchorse.blockbuster.client.render;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.trackers.ApertureCamera;
import mchorse.metamorph.client.render.MorphRenderPipeline;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Actor renderer (roadmap P80).
 *
 * <p>Port of 1.12.2 {@code client/render/RenderActor.java}: the actor draws
 * <b>its morph</b>, not a fixed model. (Until the S10 actor morph binding the
 * actor's morph was raw NBT and there was nothing to draw, so this renderer
 * fell back to a wide-arm Steve — that fallback is gone.)</p>
 *
 * <p>What legacy does, preserved here:</p>
 * <ul>
 *   <li><b>Shadow sizing per morph.</b> {@code shadowOpacity} starts at 0 and
 *   only becomes 1 when a morph actually draws, so an actor with no morph
 *   casts no shadow at all. A {@link CustomMorph} sizes its shadow from
 *   {@code getWidth(entity) * model.scale[0]}; everything else gets 0.5.</li>
 *   <li><b>Invisible actors draw nothing</b> — not even the model — but still
 *   get the debug recording nameplate.</li>
 * </ul>
 *
 * <p><b>P80.3 — both halves landed.</b> The {@code renderLast}
 * translucency-safe late pass (batch U-E): {@link #shouldRender} enqueues into
 * {@code RenderingHandler} and opts out of the normal pass, and {@link #render}
 * records the draw for the {@code actorAlwaysRender} sweep. The
 * {@code recordsToRender} F3 debug-path register (batch U-F): {@link #render}
 * feeds {@link WorldDebugRenderer} at its tail. The two are separate legacy
 * mechanisms at different points in the frame — {@code renderLastEntities}
 * versus the {@code RenderWorldLastEvent} handler — and neither decides
 * <em>whether</em> an actor draws.</p>
 *
 * <p>The base class stays a {@link LivingEntityRenderer} with a player model
 * so vanilla's leash/shadow/nameplate machinery still has a model to size
 * against, but {@link #render} never calls {@code super} — exactly like legacy,
 * whose {@code RenderLiving} was constructed with a {@code null} model.</p>
 */
public class RenderActor extends LivingEntityRenderer<EntityActor, PlayerEntityModel<EntityActor>>
{
    /** Legacy {@code blockbuster:textures/entity/actor.png}. */
    public static final Identifier ACTOR_TEXTURE = new Identifier(Blockbuster.MOD_ID, "textures/entity/actor.png");

    /** Legacy's debug-nameplate cutoff, in blocks. */
    private static final double NAME_MAX_DISTANCE = 64;

    public RenderActor(EntityRendererFactory.Context context)
    {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public Identifier getTexture(EntityActor entity)
    {
        return ACTOR_TEXTURE;
    }

    /**
     * Legacy {@code RenderActor.shouldRender}: the {@code actor_always_render}
     * config forces the actor to render even when it is outside the view
     * frustum (off-screen scene actors must keep animating).
     *
     * <p>P80.3: legacy short-circuits <b>first</b> for {@code renderLast}
     * actors — {@code if (entity.renderLast && addRenderLast(entity)) return
     * false;} — so the actor opts out of the normal pass <i>having already
     * enqueued itself</i> for the sorted tail. Order matters: the enqueue
     * happens before the always-render check, and it happens even when the
     * actor is outside the frustum. During the tail pass the re-entrancy guard
     * makes {@code addRenderLast} refuse, so this returns the ordinary answer
     * and the actor draws for real.</p>
     */
    @Override
    public boolean shouldRender(EntityActor entity, Frustum frustum, double x, double y, double z)
    {
        if (RenderingHandler.deferRenderLast(entity.renderLast, entity))
        {
            return false;
        }

        return shouldRender(Blockbuster.actorAlwaysRender.get(), super.shouldRender(entity, frustum, x, y, z));
    }

    /**
     * Pure {@code shouldRender} decision (headless-testable): the always-render
     * config OR the vanilla frustum check.
     */
    public static boolean shouldRender(boolean actorAlwaysRender, boolean superShouldRender)
    {
        return actorAlwaysRender || superShouldRender;
    }

    @Override
    public void render(EntityActor entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        /* P80.3, legacy's ASM `addRenderedEntity` after the second
         * renderEntityStatic call: record that this actor was actually drawn, so
         * the actorAlwaysRender sweep at the tail does not draw it again.
         *
         * S22 P285: not during the tracker modifier's throwaway capture pass.
         * Legacy's hook sat in RenderGlobal.renderEntities — one level ABOVE
         * the `render.doRender` the tracker re-invoked — so a captured actor
         * was never marked as drawn. Marking it here would make the
         * always-render sweep skip a tracked actor that the real pass then
         * culls, i.e. tracking an off-screen actor would delete it. */
        if (!ApertureCamera.capturing)
        {
            RenderingHandler.addRenderedEntity(entity);
        }

        this.shadowOpacity = 0F;

        if (entity.invisible)
        {
            this.renderRecordingName(entity, matrices, vertexConsumers, light);

            return;
        }

        AbstractMorph morph = entity.getMorph();

        if (morph != null)
        {
            this.shadowOpacity = 1.0F;
            this.shadowRadius = shadowSize(morph, entity);

            MorphRenderPipeline.drawEntity(morph, entity, matrices, vertexConsumers, light, tickDelta);
        }

        this.renderRecordingName(entity, matrices, vertexConsumers, light);

        /* Legacy's `RenderingHandler.recordsToRender.add(entity.playback.record)`
         * at the tail of doRender: register this actor's record for the F3
         * debug-path overlay (P80.3, drawn by WorldDebugRenderer). Its position
         * at the tail is load-bearing — the invisible branch above returns
         * early, so an invisible actor never registers and never draws a
         * path. */
        if (entity.playback != null && entity.playback.record != null)
        {
            WorldDebugRenderer.addRecord(entity.playback.record);
        }
    }

    /**
     * Legacy shadow-size rule (pure, headless-testable): a {@link CustomMorph}
     * with a model scales its shadow by the model's X scale; anything else —
     * including a CustomMorph whose model has not loaded — uses 0.5.
     */
    public static float shadowSize(AbstractMorph morph, EntityActor entity)
    {
        if (morph instanceof CustomMorph custom && custom.model != null)
        {
            return custom.getWidth(entity) * custom.model.scale[0];
        }

        return 0.5F;
    }

    /**
     * Legacy {@code renderPlayerRecordingName}: the record's filename over the
     * actor, <b>only while the F3 debug overlay is up</b> and within 64 blocks.
     * It is a debug affordance for lining scenes up, not a nameplate — which is
     * why it ignores the actor's custom name and the name-visibility rules.
     *
     * <p>Legacy drew it at half the entity's height with a half-line vertical
     * shift and pushed the whole thing through a raw GL billboard; the port
     * hands that to {@link Nameplate}, the shared {@code drawNameplate}
     * equivalent, which does the camera-facing rotate and the background quad.</p>
     */
    private void renderRecordingName(EntityActor entity, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || !mc.getDebugHud().shouldShowDebugHud() || this.dispatcher.camera == null)
        {
            return;
        }

        if (entity.playback == null || entity.playback.record == null)
        {
            return;
        }

        if (this.dispatcher.getSquaredDistanceToCamera(entity) > NAME_MAX_DISTANCE * NAME_MAX_DISTANCE)
        {
            return;
        }

        matrices.push();

        /* Legacy `y += entity.height / 2` */
        matrices.translate(0, entity.getHeight() / 2F, 0);

        try
        {
            /* Legacy's shift was -fontHeight/2 in TEXT space, applied after the
             * -0.025 mirrored scale; Nameplate takes world units before the
             * billboard, so the same offset is +fontHeight * SCALE / 2. */
            Nameplate.draw(matrices, consumers, entity.playback.record.filename,
                mc.textRenderer.fontHeight * Nameplate.SCALE / 2F, false, light);
        }
        finally
        {
            matrices.pop();
        }
    }
}
