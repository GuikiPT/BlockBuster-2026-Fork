package mchorse.metamorph.client.gui.creative;

import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.MorphRenderUtils;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Morph viewport (roadmap P84).
 *
 * <p>Port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiMorphRenderer}: the
 * perspective viewport the creative morph picker and morph editors embed to
 * preview an {@link AbstractMorph}. Extends McLib's P42
 * {@link GuiModelRenderer}, so it inherits the orbit/pan/fly camera, the
 * {@code DummyEntity} host and the {@code rendering} flag that
 * {@code MorphUtils.render}'s shadow-pass gate reads back.</p>
 *
 * <p>Legacy {@code drawUserModel} was just
 * {@code MorphUtils.render(morph, entity, 0, 0, 0, yaw, partialTicks)} — the
 * transform it drew into was the <b>ambient</b> {@code GL_MODELVIEW} the
 * viewport had already set up. This port keeps that: {@link GuiModelRenderer}
 * still writes the orbit camera into {@code RenderSystem.getModelViewStack()},
 * which becomes the {@code ModelViewMat} uniform, so the per-draw
 * {@link MatrixStack} opened here starts at <b>identity</b> and the camera
 * arrives through the global — the same split
 * {@code GuiUtils.drawEntityOnScreen} uses.</p>
 *
 * <h2>Why a render frame is opened here (P58)</h2>
 *
 * <p>The legacy render signature carries no matrices, so P54 replaced the
 * ambient {@code GL_MODELVIEW} with an explicit {@link MorphRenderContext}
 * stack — and {@code MorphRendererRegistry.Dispatcher#render} draws nothing
 * when no frame is current. The world paths open theirs in
 * {@code MorphRenderPipeline}; <b>this</b> is the GUI 3D path's frame, and
 * without it every viewport preview in the tree (picker selection, morph
 * editors, body-part editor, actor screen) resolved a renderer and then
 * dropped the draw on the floor. The {@code renderOnScreen} path needs no
 * frame — those renderers build their own off the {@code DrawContext}.</p>
 *
 * <p>The consumers are the shared entity {@code Immediate} and are flushed
 * <b>inside</b> the frame, because {@link GuiModelRenderer} pops the model-view
 * stack and restores the orthographic projection immediately after this
 * returns; geometry still sitting in the buffer at that point would be drawn
 * later under the GUI's own matrices.</p>
 */
public class GuiMorphRenderer extends GuiModelRenderer
{
    public AbstractMorph morph;

    public GuiMorphRenderer(MinecraftClient mc)
    {
        super(mc);
    }

    @Override
    protected void drawUserModel(GuiContext context)
    {
        if (this.morph == null || this.morph.errorRendering)
        {
            return;
        }

        /* Legacy MorphUtils.renderDirect: guard the draw, flip errorRendering on
         * failure and recover a shared tessellator buffer leaked by a broken
         * morph renderer (see MorphRenderUtils#render). */
        /* Deliberate legacy divergence: 1.12.2 passed this.yaw — the ORBIT
         * CAMERA's yaw — as the entityYaw render argument. Every morph that
         * reads that argument then counter-rotated with the camera; most
         * visibly the image morph, whose non-billboard branch applies
         * Ry(180 - entityYaw), so Ry(cameraYaw)·Ry(180 - cameraYaw) cancelled
         * and the picture faced the viewer from every orbit angle. The
         * preview's subject is the stationary dummy, so its own body yaw
         * (0, or the posed value when customEntity is set) is what an
         * in-world render would be handed. */
        renderInFrame(this.mc, this.morph, this.entity, 0, 0, 0, this.entity == null ? 0 : this.entity.bodyYaw, context.partialTicks);
    }

    /**
     * Draw a morph into the GUI 3D pass: open a {@link MorphRenderContext}
     * frame at identity over the shared entity consumers, run the error-trapped
     * legacy draw, and flush before the frame closes.
     *
     * <p>Shared with the picker's onion-skin ghost pass, which needs exactly
     * the same frame (per ghost, so each can carry its own tint — hence the
     * flush being part of the frame rather than one flush for the whole pass).</p>
     *
     * @return whether the morph actually drew
     */
    public static boolean renderInFrame(MinecraftClient mc, AbstractMorph morph, LivingEntity entity, double x, double y, double z, float yaw, float partialTicks)
    {
        if (mc == null || morph == null || entity == null)
        {
            return false;
        }

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();

        MorphRenderContext.push(new MatrixStack(), consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, partialTicks);

        try
        {
            return MorphRenderUtils.render(morph, entity, x, y, z, yaw, partialTicks);
        }
        finally
        {
            try
            {
                consumers.draw();
            }
            finally
            {
                MorphRenderContext.pop();
            }
        }
    }
}
