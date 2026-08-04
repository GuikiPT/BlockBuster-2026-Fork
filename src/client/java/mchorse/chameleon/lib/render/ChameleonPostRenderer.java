package mchorse.chameleon.lib.render;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.mclib.client.render.RenderingUtilsClient;
import net.minecraft.client.render.VertexConsumer;

import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

/**
 * Post render processor
 *
 * This render processors is responsible for applying given bone's transformation
 * onto OpenGL's matrix stack. Make sure you push before and pop after the matrix
 * stack when you using this!
 *
 * <p>This is what attaches body parts to bones: the morph walks to the bone the
 * part names, leaves that bone's accumulated transform on the vanilla matrix
 * stack, and the body part then draws in the bone's local frame.</p>
 *
 * <p>Port note: legacy transposed the model matrix into a {@code FloatBuffer}
 * and called {@code GlStateManager.multMatrix}. The transpose was only the
 * row-major → column-major conversion GL wanted, so the whole dance is
 * {@link RenderingUtilsClient#multiply} — which additionally multiplies the
 * frame's <b>normal</b> matrix, something the fixed-function pipeline used to
 * derive on its own and {@code MatrixStack.multiplyPositionMatrix} deliberately
 * does not do.</p>
 *
 * Legacy source: chameleon/.../lib/render/ChameleonPostRenderer.java
 */
public class ChameleonPostRenderer implements IChameleonRenderProcessor
{
    private static Matrix4f matrix = new Matrix4f();

    private String boneName = "";

    private net.minecraft.client.util.math.MatrixStack matrices;

    /**
     * Multiply given matrix stack onto the vanilla matrix stack
     */
    public static void multiplyMatrix(MatrixStack stack, ModelBone bone, net.minecraft.client.util.math.MatrixStack matrices)
    {
        matrix.set(stack.getModelMatrix());

        RenderingUtilsClient.multiply(matrices, matrix);

        Vector3f pivot = bone.initial.translate;

        matrices.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
    }

    public void setBoneName(String boneName)
    {
        this.boneName = boneName;
    }

    public void setMatrices(net.minecraft.client.util.math.MatrixStack matrices)
    {
        this.matrices = matrices;
    }

    @Override
    public boolean renderBone(VertexConsumer builder, MatrixStack stack, ModelBone bone)
    {
        if (bone.id.equals(this.boneName))
        {
            multiplyMatrix(stack, bone, this.matrices);

            return true;
        }

        return false;
    }
}
