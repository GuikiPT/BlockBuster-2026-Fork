package mchorse.vanilla_pack.render;

import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.vanilla_pack.morphs.BlockMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

/**
 * Client render body for {@link BlockMorph} (roadmap P54/P53.2).
 *
 * <p>Legacy declared both halves on the morph itself behind
 * {@code @SideOnly(CLIENT)}; in the split source set they live here. Both go
 * through {@code BlockRendererDispatcher.renderBlockBrightness(state, 1F)}'s
 * 1.20.4 successor, {@link BlockRenderManager#renderBlockAsEntity} — the same
 * "draw this blockstate detached from a chunk, at a fixed brightness" entry
 * point, which also removes the legacy manual block-atlas bind (the render
 * layer carries the texture now).</p>
 *
 * <p><b>The successor is not a drop-in (roadmap P289).</b> 1.12.2's
 * {@code renderBlockBrightness} rotated the frame {@code +90°} about Y before
 * emitting any quad; {@code renderBlockAsEntity} does not. Copying the legacy op
 * list verbatim therefore left a stray {@code -90°} that rotated the centring
 * offset onto the wrong axes and put the block a full block off on X — the
 * user-reported "off-centre, +1 on X fixes it". Both paths now end with
 * {@link RenderingUtilsClient#blockBrightnessQuarterTurn}, which is what vanilla
 * 1.20.4's own {@code TntEntityRenderer} does at the same point.</p>
 *
 * <p><b>The {@code lighting} flag.</b> {@link mchorse.vanilla_pack.morphs.ItemStackMorph#lighting}
 * is a <i>fullbright</i> toggle read inverted: legacy pinned the lightmap coords
 * to {@code (240, 240)} when it was <b>false</b> and restored them after. Here
 * that is the packed-light argument, so {@code lighting == false} passes
 * {@link MorphRenderContext#FULL_BRIGHT} and no save/restore is needed —
 * the light is per-draw rather than global GL state.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/BlockMorph.java (render/renderOnScreen)
 */
public class BlockMorphRenderer implements IMorphRenderer<BlockMorph>
{
    /* --------------------------------------------------------------------- */
    /* Transforms (pure — pinned by BlockMorphTransformTest)                 */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code BlockMorph.render}'s op list, plus the quarter-turn
     * {@link RenderingUtilsClient#blockBrightnessQuarterTurn} restores
     * (roadmap P289).
     *
     * <p>The composed transform is exactly {@code translate(x, y, z) ·
     * translate(-0.5, -0.5, -0.5) · translate(0, 0.5, 0)} — i.e. the unit block
     * ends up spanning {@code [x-0.5, x+0.5] × [y, y+1] × [z-0.5, z+0.5]}:
     * <b>centred on the entity horizontally, standing on its feet</b>. The
     * rotate/translate/rotate spelling is kept rather than collapsed to the
     * single translate so the chain still diffs one-for-one against
     * {@code .tools/legacy-src/.../BlockMorph.java} and against vanilla's
     * {@code TntEntityRenderer}, which is the class it was copied from.</p>
     *
     * <p>Note what is <i>not</i> here: no {@code entityYaw}. Legacy never yawed
     * the block with the player, so a block morph stays world-axis-aligned from
     * every facing — turning around must not move or spin it.</p>
     */
    public static void applyWorldTransform(MatrixStack matrices, double x, double y, double z)
    {
        matrices.translate(x, y + 0.5F, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
        matrices.translate(-0.5F, -0.5F, 0.5F);
        RenderingUtilsClient.blockBrightnessQuarterTurn(matrices);
    }

    /**
     * Legacy {@code BlockMorph.renderOnScreen}'s op list, plus the same
     * quarter-turn. The GUI path needs the compensation just as much as the
     * world path — without it the thumbnail's block sits a whole unit off along
     * the pre-rotation Z, which the 45°/45° view turns into a visible drift in
     * the picker cell. (X-C found the picker silently diverging from the world
     * path in another subsystem this wave; here they share the same seam so they
     * cannot.)
     */
    public static void applyScreenTransform(MatrixStack matrices, int x, int y, float scale)
    {
        matrices.translate(x, y, 0);
        matrices.scale(-scale, -scale, -scale);
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(45.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));
        matrices.translate(0, 1, 0);
        RenderingUtilsClient.blockBrightnessQuarterTurn(matrices);
    }

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(BlockMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || morph.block == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        BlockRenderManager blocks = mc.getBlockRenderManager();

        if (blocks == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices;

        matrices.push();

        try
        {
            applyWorldTransform(matrices, x, y, z);

            blocks.renderBlockAsEntity(morph.block, matrices, context.consumers,
                VanillaPackMorphRenderers.lightOf(morph.lighting, context.light), OverlayTexture.DEFAULT_UV);
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy: "Render in GUIs just like any other entity, 45 degree rotate by X
     * and Y." The uniform negative scale is safe through
     * {@link MatrixStack#scale} — it only mangles the normal matrix when the
     * magnitudes differ, which is why {@code CustomMorphRenderer}'s mirrored
     * <i>non</i>-uniform scales have to go through {@code multiplyPositionMatrix}
     * and this one does not.
     */
    @Override
    public void renderOnScreen(BlockMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (dc == null || mc == null || morph.block == null)
        {
            return;
        }

        BlockRenderManager blocks = mc.getBlockRenderManager();

        if (blocks == null)
        {
            return;
        }

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            applyScreenTransform(matrices, x, y, scale);

            blocks.renderBlockAsEntity(morph.block, matrices, consumers,
                MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV);

            consumers.draw();
        }
        finally
        {
            matrices.pop();
        }
    }
}
