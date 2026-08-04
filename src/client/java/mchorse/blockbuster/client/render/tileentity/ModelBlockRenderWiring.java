package mchorse.blockbuster.client.render.tileentity;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Installs the model-block render seams (roadmap P229).
 *
 * <p>{@link TileEntityModelRenderer} landed in P96 structurally complete and
 * completely inert: {@code morphDrawer}, {@code entityMorphOverride} and
 * {@code shadowRenderer} all defaulted to null and <b>nothing assigned them,
 * not even a test</b>. A model block therefore ticked, culled, updated its dummy
 * actor and drew nothing; the same was true of the model block held in hand, in
 * an inventory slot or in an item frame, whose drawers defaulted to
 * {@code InertDrawer}. This class is the missing assignment, and the five seams
 * are wired together because they are one feature.</p>
 *
 * <p>Everything it installs already existed by the time this ran — the morph
 * render pipeline (P54) is installed from {@code MetamorphClient.init()},
 * {@code EntityUtils.getMorph} is the P53 accessor, and the shadow is vanilla's
 * own. The only reason the block was dark is that nobody connected them.</p>
 *
 * <p>Per the S22 shared-file protocol this is the whole wiring: one
 * {@code install()}, called from exactly one line in {@code BlockbusterClient}.</p>
 */
public final class ModelBlockRenderWiring
{
    private ModelBlockRenderWiring()
    {}

    public static void install()
    {
        /* The morph draw (legacy MorphUtils.render against the block's dummy
         * actor). */
        TileEntityModelRenderer.morphDrawer = ModelBlockRenderWiring::drawMorph;

        /* Legacy EntityUtils.getMorph(entity): a morphed host entity wins over
         * the block's own morph. For the stock dummy actor this returns null and
         * the TE morph is used — it only fires for a morph-providing host. */
        TileEntityModelRenderer.entityMorphOverride = EntityUtils::getMorph;

        /* Legacy RenderShadow.doRenderShadowAndFire. */
        TileEntityModelRenderer.shadowRenderer = ModelBlockShadow::draw;

        /* The item halves of the same block (legacy TEISR + the mesh-definition
         * config switch). */
        TileEntityModelItemStackRenderer.liveDrawer = TileEntityModelItemStackRenderer::drawLive;
        TileEntityModelItemStackRenderer.staticDrawer = ModelBlockStaticItemModel::draw;

        ModelBlockStaticItemModel.register();
    }

    /**
     * The {@link TileEntityModelRenderer.MorphDrawer} implementation: open a
     * render frame at the caller's matrices and go through the error-trapped
     * {@link MorphRenderUtils#render} — the same trap the player, actor and gun
     * paths use, so a throwing morph latches {@code errorRendering} once (which
     * is what turns the block's F3 debug cube red) instead of spamming.
     *
     * <p>The legacy arguments are kept exactly: {@code (morph, entity, 0, 0, 0,
     * 0, partialTicks)}. The zero offsets are because the caller has already
     * translated to the settings position, and the <b>zero yaw</b> is legacy's —
     * not the entity's body yaw, which the renderer has separately written onto
     * the dummy from {@code settings.getRotateBody()} and which the morph
     * renderers read off the entity themselves.</p>
     *
     * <p>Unlike {@code MorphRenderPipeline.drawEntity} there is no
     * {@code canDraw} pre-check: that gate exists so the player-render mixin can
     * fall back to the vanilla player, and a model block has nothing to fall
     * back to — legacy called the draw unconditionally.</p>
     */
    public static void drawMorph(AbstractMorph morph, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, float tickDelta)
    {
        if (morph == null)
        {
            return;
        }

        MorphRenderContext.push(matrices, vertexConsumers, light, overlay, tickDelta);

        try
        {
            MorphRenderUtils.render(morph, entity, 0, 0, 0, 0, tickDelta);
        }
        finally
        {
            MorphRenderContext.pop();
        }
    }
}
