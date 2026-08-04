package mchorse.metamorph.client.render;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.vanilla_pack.render.VanillaPackMorphRenderers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Installs the morph render pipeline at client init (roadmap P54).
 *
 * <p>Three seams get filled here, and they are the reason nothing drew before:
 * {@link MorphRenderer#provider} (where the player's morph state comes from),
 * {@link MorphRenderer#drawer} (how a morph is drawn once the transition
 * transform is applied), and {@link AbstractMorph#renderDispatcher} (how a
 * morph's own {@code render}/{@code renderOnScreen} reach a renderer). With all
 * three in place, every already-routed preview in the GUI tree — picker cells,
 * body-part rows, gun slots, scene/record/action pickers — lights up at once.</p>
 *
 * <p>Blockbuster's own pack morph renderers register from
 * {@code BlockbusterClient} before {@link #install()} runs; the registry
 * invalidates its resolution cache on every registration, so the order is not
 * load-bearing either way.</p>
 */
public final class MorphRenderPipeline
{
    private MorphRenderPipeline()
    {}

    public static void install()
    {
        MorphRendererRegistry.register(EntityMorph.class, new EntityMorphRenderer());
        VanillaPackMorphRenderers.register();
        MorphRendererRegistry.install();

        MorphRenderer.provider = MorphRenderPipeline::stateOf;
        MorphRenderer.drawer = MorphRenderPipeline::draw;
    }

    /**
     * Adapt the P52 morphing component to {@link MorphRenderer.MorphState}.
     * A player without the component (or before it is attached) yields null,
     * which keeps the player-render mixin inert.
     */
    public static MorphRenderer.MorphState stateOf(PlayerEntity player)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing == null)
        {
            return null;
        }

        return new MorphRenderer.MorphState(morphing.getCurrentMorph(), morphing.getPreviousMorph(), morphing.getAnimation());
    }

    /**
     * The draw seam: open a render frame at the caller's matrices, then go
     * through {@link MorphRenderUtils} so the {@code errorRendering} latch and
     * the shared-tessellator recovery apply exactly as they did on 1.12.2.
     *
     * <p>The caller's {@link MatrixStack} is already positioned at the entity
     * (the player-render mixin injects where vanilla would draw the model) and
     * carries the morph-transition transform, so the legacy {@code x/y/z}
     * offsets are zero here — mirroring legacy {@code Morphing.renderPlayer},
     * which also translated first and then rendered at the origin.</p>
     */
    public static boolean draw(AbstractMorph morph, PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta)
    {
        return drawEntity(morph, player, matrices, consumers, light, tickDelta);
    }

    /**
     * Whether this morph has a client renderer at all. Pure, so the deliberate
     * "unported render body → keep the vanilla player" deviation is pinned by a
     * test rather than only by the draw path.
     */
    public static boolean canDraw(AbstractMorph morph)
    {
        return morph != null && MorphRendererRegistry.get(morph) != null;
    }

    /**
     * Shared body of {@link #draw} for any living entity (the gun renderers
     * draw morphs against a dummy actor rather than a player).
     *
     * <p>Returns whether a draw actually happened. A morph whose client
     * renderer has not landed yet reports {@code false} <b>before</b> the frame
     * is opened, which is what lets the player-render mixin fall back to the
     * vanilla player instead of leaving a hole where the morph would be.</p>
     */
    public static boolean drawEntity(AbstractMorph morph, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light, float tickDelta)
    {
        if (!canDraw(morph) || entity == null)
        {
            return false;
        }

        float yaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);

        /* The hurt flash. 1.12.2 tinted the model by poking the fixed-function
         * lightmap (McLib's RenderLightmap.set, called from each morph's own
         * render body); on 1.20.4 it is the per-vertex overlay UV, and the frame
         * is where every renderer reads it from — EntityMorphRenderer,
         * CustomMorphRenderer and RenderCustomModel all thread context.overlay
         * down to the vertex, and every layer they draw through enables
         * ENABLE_OVERLAY_COLOR. Pushing DEFAULT_UV here therefore switched the
         * flash off for all of them at once. ChameleonMorphRenderer only looked
         * unaffected because it ignores the frame and calls getOverlay itself.
         *
         * getOverlay's second argument is the white flash (creeper ignition);
         * vanilla's LivingEntityRenderer.getAnimationCounter returns 0 for
         * everything else, which is what EntityMorphBodyParts already passes. */
        MorphRenderContext.push(matrices, consumers, light,
            LivingEntityRenderer.getOverlay(entity, 0F), tickDelta);

        try
        {
            return MorphRenderUtils.render(morph, entity, 0, 0, 0, yaw, tickDelta);
        }
        finally
        {
            MorphRenderContext.pop();
        }
    }
}
