package mchorse.chameleon.metamorph.editor;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.render.ChameleonRenderer;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.chameleon.metamorph.editor.render.ChameleonHighlightRenderer;
import mchorse.chameleon.metamorph.editor.render.ChameleonStencilRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.metamorph.client.gui.creative.GuiMorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * The 3D preview inside the Chameleon morph editor: the normal morph draw, plus
 * the blue highlight over the selected bone, plus the ctrl+click stencil bone
 * picker.
 *
 * <p>Port notes: the GL state legacy toggled by hand around each pass
 * ({@code disableTexture2D}, {@code disableDepth}, {@code disableCull},
 * {@code color}) is either {@link RenderSystem} state or part of the render
 * layer here; the explicit {@code glClear(GL_DEPTH_BUFFER_BIT)} before the
 * stencil pass is gone because {@code GuiModelRenderer.tryPicking} now binds and
 * clears a dedicated offscreen depth24-stencil8 framebuffer for that pass.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/GuiChameleonModelRenderer.java
 */
public class GuiChameleonModelRenderer extends GuiMorphRenderer
{
    private static final MatrixStack MATRIX_STACK = new MatrixStack();
    private static final ChameleonStencilRenderer STENCIL_RENDERER = new ChameleonStencilRenderer();
    private static final ChameleonHighlightRenderer HIGHLIGHT_RENDERER = new ChameleonHighlightRenderer();

    /** A 1x1 white texture, so the stencil pass always has a cutout layer. */
    private static final Identifier BLANK = new Identifier("mclib", "textures/pixel.png");

    public String boneName = "";

    public GuiChameleonModelRenderer(MinecraftClient mc)
    {
        super(mc);
    }

    @Override
    protected void drawUserModel(GuiContext context)
    {
        super.drawUserModel(context);
        this.drawHighlight(context);
        this.tryPicking(context);
    }

    private void drawHighlight(GuiContext context)
    {
        if (!(this.morph instanceof ChameleonMorph))
        {
            return;
        }

        ChameleonModel model = ((ChameleonMorph) this.morph).getModel();

        if (model == null || this.boneName.isEmpty())
        {
            return;
        }

        float scale = ((ChameleonMorph) this.morph).scale;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        net.minecraft.client.util.math.MatrixStack matrices = new net.minecraft.client.util.math.MatrixStack();

        matrices.push();

        try
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - (this.customEntity ? this.entityYawBody : 0)));
            matrices.scale(scale, scale, scale);

            HIGHLIGHT_RENDERER.setBoneName(this.boneName);
            HIGHLIGHT_RENDERER.setMatrices(matrices);

            ChameleonRenderer.processRenderModel(HIGHLIGHT_RENDERER, null, MATRIX_STACK, model.model);
        }
        finally
        {
            matrices.pop();

            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void drawForStencil(GuiContext context)
    {
        if (!(this.morph instanceof ChameleonMorph) || this.mc == null)
        {
            return;
        }

        ChameleonMorph morph = (ChameleonMorph) this.morph;
        ChameleonModel model = morph.getModel();

        if (model == null)
        {
            return;
        }

        float scale = morph.scale;
        Identifier texture = morph.skin == null ? BLANK : morph.skin.toIdentifier();

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        net.minecraft.client.util.math.MatrixStack matrices = new net.minecraft.client.util.math.MatrixStack();

        matrices.push();

        try
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - (this.customEntity ? this.entityYawBody : 0)));
            matrices.scale(scale, scale, scale);

            VertexConsumerProvider.Immediate immediate = this.mc.getBufferBuilders().getEntityVertexConsumers();
            /* Cutout, so fully transparent texels are discarded and stay
             * unpickable — 1.12.2 got that from the fixed-function alpha test.
             * Same reasoning (and the same 0.1 threshold deviation) as
             * GuiBBModelRenderer.stencilLayer, P286. */
            RenderLayer layer = RenderLayer.getEntityCutoutNoCull(texture);

            STENCIL_RENDERER.setBones(model.getBoneNames());
            STENCIL_RENDERER.setup(() ->
            {
                immediate.draw();

                return immediate.getBuffer(layer);
            }, matrices, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

            ChameleonRenderer.processRenderModel(STENCIL_RENDERER, null, MATRIX_STACK, model.model);

            immediate.draw();
        }
        catch (Exception e)
        {
            /* No GL context / bad model — picking yields nothing this frame. */
        }
        finally
        {
            matrices.pop();

            RenderSystem.enableCull();
        }
    }

    /**
     * Invert {@link ChameleonStencilRenderer}'s "bone index + 1" encoding. 0 is
     * "nothing was hit", so it maps to {@code null}.
     */
    @Override
    protected String getStencilValue(int value)
    {
        if (value == 0)
        {
            return null;
        }

        value -= 1;

        if (this.morph instanceof ChameleonMorph)
        {
            ChameleonMorph morph = (ChameleonMorph) this.morph;
            ChameleonModel model = morph.getModel();

            if (model != null)
            {
                List<String> bones = model.getBoneNames();

                if (value >= 0 && value < bones.size())
                {
                    return bones.get(value);
                }
            }
        }

        return super.getStencilValue(value);
    }
}
