package mchorse.blockbuster_pack.client.render;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.client.render.ModelTransforms;
import mchorse.blockbuster_pack.morphs.StructureMorph;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderer;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderers;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.vanilla_pack.render.VanillaPackMorphRenderers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;

/**
 * Client render body for {@link StructureMorph} (roadmap P54/P162).
 *
 * <p>The heavy half — decoding a structure template into a fake block view and
 * emitting its blocks and block entities — is {@link StructureRenderer}, which
 * landed with P162. This class is what legacy kept on the morph: the load-state
 * check, the transform chain and the two draw calls. It also replaces the
 * render half of the old {@code StructureMorph.IStructureClient} seam, which
 * existed only because there was nowhere else to put a client draw; the cache
 * and the re-request machinery stay on {@link StructureRenderers}.</p>
 *
 * <p><b>Animation.</b> An in-progress animation is applied to a <i>copy</i> of
 * the pose (legacy allocated a fresh {@code ModelTransform} rather than mutating
 * the morph's own), and the anchor is interpolated alongside it, but only when
 * {@code lastAnchorX} was captured — a morph that never merged from a previous
 * structure keeps its anchor fixed.</p>
 *
 * <p><b>Lighting.</b> Legacy drew the geometry with a vertex-format trick that
 * swapped in world lighting per block, and set the lightmap to fullbright when
 * {@code lighting} was off. Here the packed light picked by
 * {@link VanillaPackMorphRenderers#lightOf} (entity light, or fullbright when
 * the flag is off) is threaded into the fake view, which the per-face bake and
 * the block entities then sample — one uniform sample for the whole structure,
 * the closest core-profile analogue to the retained-buffer swap (see the S14
 * parity note / open question #2 and {@code FakeBlockRenderView}'s notes).</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster_pack/morphs/StructureMorph.java (render/renderOnScreen)
 */
public class StructureMorphRenderer implements IMorphRenderer<StructureMorph>
{
    /** Legacy's on-screen fit factor: {@code scale /= 0.65 * largest dimension}. */
    public static final float GUI_FIT = 0.65F;

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(StructureMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        StructureRenderer renderer = StructureRenderers.ensureLoaded(morph);

        if (renderer == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices;
        int light = VanillaPackMorphRenderers.lightOf(morph.lighting, context.light);

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            ModelTransforms.apply(matrices, pose(morph, partialTicks));
            matrices.translate(anchorX(morph, partialTicks), anchorY(morph, partialTicks), anchorZ(morph, partialTicks));

            renderer.render(matrices, context.consumers, morph, light, context.overlay);
            renderer.renderTEs(matrices, context.consumers, morph, partialTicks, light, context.overlay);
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
     * Legacy: fit the structure's largest dimension into the icon, then the
     * same 45°/45° isometric view every block-ish morph preview uses. The
     * anchor and pose are deliberately <b>not</b> applied here — legacy's
     * on-screen path drew the raw structure, so a posed structure morph still
     * previews upright in the picker.
     *
     * <p><b>Winding.</b> The GUI orthographic projection is Y-down, which
     * mirrors triangle winding relative to world rendering — vanilla
     * compensates with a Y-flip (see {@code DrawContext.drawItem}'s
     * {@code scaling(1, -1, 1)}). The legacy op list ended in a
     * {@code 180°Z + 180°Y} rotation pair, which turns the model upright but,
     * being a rotation (determinant +1), cannot restore the handedness — every
     * face culled inverted and structures previewed inside-out. The uniform
     * negative scale (determinant &lt; 0) is the block-morph picker's proven
     * spelling of the same fix ({@code BlockMorphRenderer.applyScreenTransform},
     * whose {@code renderBlockAsEntity}-specific quarter-turn and unit-block
     * centering do not apply here); a one-block structure now previews with
     * the same orientation as the equivalent block morph.</p>
     */
    @Override
    public void renderOnScreen(StructureMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        StructureRenderer renderer = StructureRenderers.ensureLoaded(morph);
        DrawContext dc = GuiDraw.getDrawContext();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (renderer == null || dc == null || mc == null || renderer.size == null)
        {
            return;
        }

        float fit = scale / (GUI_FIT * largestDimension(renderer));

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            /* Lift the preview in front of the GUI plane. 1.20.4's 2D GUI
             * layers depth-test live at z = 0 (see MorphRendererRegistry.
             * resetGuiDepth), and the isometric view puts the bottom face's
             * far corners at screen z down to -fit·(w+d)/4 — those fragments
             * would be depth-rejected wherever the cell already painted
             * (selection highlight, drop shadow), slicing the preview along a
             * diagonal plane. Same reason the custom-model preview chain sits
             * at z = 50; the registry clears our depth footprint afterwards. */
            float lift = fit * 0.25F * (renderer.size.getX() + renderer.size.getZ()) + 1.0F;

            matrices.translate(x, y, lift);
            matrices.scale(-fit, -fit, -fit);
            matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(45.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F));

            renderer.render(matrices, consumers, morph, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
            renderer.renderTEs(matrices, consumers, morph, mc.getTickDelta(), MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV);

            consumers.draw();
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Pure halves (headless-testable)                                       */
    /* --------------------------------------------------------------------- */

    public static int largestDimension(StructureRenderer renderer)
    {
        return Math.max(renderer.size.getX(), Math.max(renderer.size.getY(), renderer.size.getZ()));
    }

    /**
     * The pose to draw with: the morph's own while idle, or a copy with the
     * animation applied while one runs. The copy matters — applying the
     * animation to {@code morph.pose} would make the interpolation compound
     * frame over frame.
     */
    public static ModelTransform pose(StructureMorph morph, float partialTicks)
    {
        if (!morph.animation.isInProgress())
        {
            return morph.pose;
        }

        ModelTransform transform = new ModelTransform();

        transform.copy(morph.pose);
        morph.animation.apply(transform, partialTicks);

        return transform;
    }

    public static float anchorX(StructureMorph morph, float partialTicks)
    {
        return anchor(morph, morph.animation.lastAnchorX, morph.anchorX, partialTicks);
    }

    public static float anchorY(StructureMorph morph, float partialTicks)
    {
        return anchor(morph, morph.animation.lastAnchorY, morph.anchorY, partialTicks);
    }

    public static float anchorZ(StructureMorph morph, float partialTicks)
    {
        return anchor(morph, morph.animation.lastAnchorZ, morph.anchorZ, partialTicks);
    }

    /**
     * Legacy's anchor interpolation, including its null guard: the anchor only
     * animates when a previous structure's anchor was captured on merge
     * ({@code lastAnchorX != null}), otherwise it snaps.
     */
    private static float anchor(StructureMorph morph, Float last, float target, float partialTicks)
    {
        if (!morph.animation.isInProgress() || last == null)
        {
            return target;
        }

        return morph.animation.interp.interpolate(last, target, morph.animation.getFactor(partialTicks));
    }
}
