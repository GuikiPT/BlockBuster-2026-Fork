package mchorse.chameleon.lib.render;

import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.utils.MatrixStack;
import net.minecraft.client.render.VertexConsumer;

/**
 * The bone-tree walk every Chameleon draw goes through, plus the two concrete
 * entry points: draw the model, and walk to one bone and leave its transform on
 * the matrix stack.
 *
 * <p>Port note: legacy's {@code render(Model)} opened and closed a
 * {@link net.minecraft.client.render.Tessellator} draw and set the GL state
 * itself ({@code disableCull}, {@code enableBlend},
 * {@code blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)}, {@code enableRescaleNormal}).
 * On 1.20.4 all of that <b>is</b> the {@code RenderLayer} the caller picks —
 * {@code RenderLayer.getEntityTranslucent} carries exactly that state, including
 * the disabled culling — and batching is the buffer's job, so this method takes
 * a ready {@link VertexConsumer} and does not touch global state.</p>
 *
 * Legacy source: chameleon/.../lib/render/ChameleonRenderer.java
 */
public class ChameleonRenderer
{
    private static final MatrixStack MATRIX_STACK = new MatrixStack();
    private static final ChameleonCubeRenderer CUBE_RENDERER = new ChameleonCubeRenderer();
    private static final ChameleonPostRenderer POST_RENDERER = new ChameleonPostRenderer();
    private static final ChameleonAxisRenderer AXIS_RENDERER = new ChameleonAxisRenderer();

    /* Specific utility methods */

    /**
     * Just render given model
     *
     * <p>The texture is part of {@code builder}'s render layer, so there is
     * nothing to bind beforehand any more.</p>
     */
    public static void render(Model model, net.minecraft.client.util.math.MatrixStack matrices, VertexConsumer builder, int light, int overlay)
    {
        CUBE_RENDERER.setup(matrices, light, overlay);

        processRenderModel(CUBE_RENDERER, builder, MATRIX_STACK, model);

        /* Legacy left the axis pass in, commented out, between two GL state
         * flips. Kept in the same shape:
         *
         * AXIS_RENDERER.setMatrices(matrices);
         * processRenderModel(AXIS_RENDERER, builder, MATRIX_STACK, model); */
    }

    /**
     * Post render (multiply the current matrix stack by bone's
     * transformations by given name)
     */
    public static boolean postRender(Model model, String boneName, net.minecraft.client.util.math.MatrixStack matrices)
    {
        POST_RENDERER.setBoneName(boneName);
        POST_RENDERER.setMatrices(matrices);

        /* Vertex consumer isn't used in this render processor, but just in case */
        return processRenderModel(POST_RENDERER, null, MATRIX_STACK, model);
    }

    /* Generic render methods */

    /**
     * Process/render given model
     *
     * This method recursively goes through all bones in the model, and
     * applies given render processor. Processor may return true from its
     * sole method which means that iteration should be halted
     */
    public static boolean processRenderModel(IChameleonRenderProcessor renderProcessor, VertexConsumer builder, MatrixStack stack, Model model)
    {
        for (ModelBone bone : model.bones)
        {
            if (processRenderRecursively(renderProcessor, builder, stack, bone))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Apply the render processor, recursively
     */
    private static boolean processRenderRecursively(IChameleonRenderProcessor renderProcessor, VertexConsumer builder, MatrixStack stack, ModelBone bone)
    {
        stack.push();
        stack.translateBone(bone);
        stack.moveToBonePivot(bone);
        stack.rotateBone(bone);
        stack.scaleBone(bone);
        stack.moveBackFromBonePivot(bone);

        if (bone.visible)
        {
            if (renderProcessor.renderBone(builder, stack, bone))
            {
                stack.pop();

                return true;
            }

            for (ModelBone childBone : bone.children)
            {
                if (processRenderRecursively(renderProcessor, builder, stack, childBone))
                {
                    stack.pop();

                    return true;
                }
            }
        }

        stack.pop();

        return false;
    }
}
