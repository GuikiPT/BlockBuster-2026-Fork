package mchorse.blockbuster.client.render;

import mchorse.blockbuster.api.ModelTransform;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * The client half of {@link ModelTransform} (roadmap P54): its 1.12.2 GL apply
 * methods, against a {@link MatrixStack}.
 *
 * <p>{@code ModelTransform} lives in the common source set (it is a disk
 * format), so {@code transform()}/{@code applyTranslate()}/{@code applyRotate()}/
 * {@code applyScale()} could not come with it. The rotation order is the
 * load-bearing part and is preserved verbatim: <b>Z, then Y, then X</b>.</p>
 *
 * <p>The three phases stay separate rather than collapsing into
 * {@link #apply}, because {@code ImageMorph} interleaves them with its billboard
 * and entity-facing rotations — translate before the facing rotation, rotate
 * after it, scale last.</p>
 *
 * Legacy source: blockbuster-1.12/.../blockbuster/api/ModelTransform.java (transform/applyTranslate/applyRotate/applyScale)
 */
public final class ModelTransforms
{
    private ModelTransforms()
    {}

    /** Legacy {@code transform()}: translate, then rotate, then scale. */
    public static void apply(MatrixStack matrices, ModelTransform transform)
    {
        applyTranslate(matrices, transform);
        applyRotate(matrices, transform);
        applyScale(matrices, transform);
    }

    public static void applyTranslate(MatrixStack matrices, ModelTransform transform)
    {
        matrices.translate(transform.translate[0], transform.translate[1], transform.translate[2]);
    }

    public static void applyRotate(MatrixStack matrices, ModelTransform transform)
    {
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(transform.rotate[2]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(transform.rotate[1]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(transform.rotate[0]));
    }

    public static void applyScale(MatrixStack matrices, ModelTransform transform)
    {
        matrices.scale(transform.scale[0], transform.scale[1], transform.scale[2]);
    }
}
