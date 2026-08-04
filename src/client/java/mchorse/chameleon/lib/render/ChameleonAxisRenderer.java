package mchorse.chameleon.lib.render;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.mclib.client.Draw;
import net.minecraft.client.render.VertexConsumer;

/**
 * Debug processor: draws a small RGB axis gizmo at every bone's pivot.
 *
 * <p>Kept because legacy kept it — {@link ChameleonRenderer} has the call
 * commented out, so nothing reaches this in a normal frame. It is the
 * "where is this bone actually pointing" tool you uncomment while debugging a
 * transform.</p>
 *
 * Legacy source: chameleon/.../lib/render/ChameleonAxisRenderer.java
 */
public class ChameleonAxisRenderer implements IChameleonRenderProcessor
{
    private net.minecraft.client.util.math.MatrixStack matrices;

    public void setMatrices(net.minecraft.client.util.math.MatrixStack matrices)
    {
        this.matrices = matrices;
    }

    @Override
    public boolean renderBone(VertexConsumer builder, MatrixStack stack, ModelBone bone)
    {
        if (this.matrices == null)
        {
            return false;
        }

        this.matrices.push();
        ChameleonPostRenderer.multiplyMatrix(stack, bone, this.matrices);

        Draw.axis(this.matrices, 0.2F);

        this.matrices.pop();

        return false;
    }
}
