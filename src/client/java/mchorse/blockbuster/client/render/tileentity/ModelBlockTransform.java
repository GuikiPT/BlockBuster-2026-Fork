package mchorse.blockbuster.client.render.tileentity;

import mchorse.blockbuster.common.tileentity.TileEntityModelSettings;
import mchorse.mclib.utils.MatrixUtils.RotationOrder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Pure model-block transform helper (roadmap P96).
 *
 * <p>Extraction of the legacy
 * {@code TileEntityModelRenderer.transform(TileEntityModel)} rotation + scale
 * math into a headlessly testable unit. The legacy code post-multiplied the
 * current {@code GlStateManager} matrix via {@code rotate}/{@code scale}; the
 * yarn {@link MatrixStack} equivalents ({@code multiply(Quaternionf)} /
 * {@code scale}) post-multiply the same way, so the resulting matrix is
 * identical when the GL call order is reproduced verbatim.</p>
 *
 * <p>Load-bearing quirks copied 1:1 (see the P96 plan):</p>
 * <ul>
 * <li><b>Rotation order</b>: the {@link RotationOrder#ZYX} enum value applies
 * the GL rotates in <b>X → Y → Z</b> call order; every other order applies
 * <b>Z → Y → X</b>. This is the enum's historical meaning under GL
 * post-multiplication — the name is <i>not</i> read literally.</li>
 * <li><b>Uniform scale</b>: when {@link TileEntityModelSettings#isUniform()},
 * {@code scale(sx, sx, sx)} — needed for backwards compatibility with old model
 * blocks that only ever set {@code sx}; never migrate {@code Scale=true} data to
 * per-axis factors.</li>
 * </ul>
 */
public final class ModelBlockTransform
{
    /**
     * The block-centring half of the legacy placement, split out of the
     * renderer so the whole chain has one source of truth (roadmap P278).
     *
     * <p>Legacy {@code TileEntityModelRenderer.render}:</p>
     *
     * <pre>
     * float xx = (float) x + 0.5F + teSettings.getX();
     * float yy = (float) y + teSettings.getY();
     * float zz = (float) z + 0.5F + teSettings.getZ();
     * GlStateManager.translate(xx, yy, zz);
     * </pre>
     *
     * <p>where {@code x/y/z} are the TESR's block-corner-relative coordinates
     * (1.12.2 {@code TileEntityRendererDispatcher} passes
     * {@code pos - staticPlayer}, and the GL matrix has the camera at the
     * origin) — precisely what 1.20.4's {@code WorldRenderer} has already put on
     * the {@link MatrixStack} before it calls a {@code BlockEntityRenderer}. So
     * on 1.20.4 the constants stand alone.</p>
     *
     * <p><b>X and Z are centred, Y is not.</b> {@code CENTER_Y} is 0: the morph
     * stands on the block's <i>bottom</i> face, not in the middle of the block.
     * That asymmetry is 1.12.2's and is load-bearing — every scene composed
     * since 2016 places models against it.</p>
     */
    public static final float CENTER_X = 0.5F;

    /** See {@link #CENTER_X} — deliberately 0, not 0.5. */
    public static final float CENTER_Y = 0.0F;

    /** See {@link #CENTER_X}. */
    public static final float CENTER_Z = 0.5F;

    private ModelBlockTransform()
    {}

    /** Legacy {@code 0.5F + teSettings.getX()}. */
    public static float offsetX(TileEntityModelSettings settings)
    {
        return CENTER_X + settings.getX();
    }

    /** Legacy {@code teSettings.getY()} — no centring term, see {@link #CENTER_X}. */
    public static float offsetY(TileEntityModelSettings settings)
    {
        return CENTER_Y + settings.getY();
    }

    /** Legacy {@code 0.5F + teSettings.getZ()}. */
    public static float offsetZ(TileEntityModelSettings settings)
    {
        return CENTER_Z + settings.getZ();
    }

    /**
     * The translate half of the legacy chain: block centre on X/Z, block bottom
     * on Y, plus the settings' shift. Must run <b>before</b> {@link #apply} —
     * legacy rotated and scaled about the shifted position, not about the block
     * centre, so a model block with both an offset and a rotation orbits its
     * offset point.
     */
    public static void place(MatrixStack matrices, TileEntityModelSettings settings)
    {
        matrices.translate(offsetX(settings), offsetY(settings), offsetZ(settings));
    }

    /**
     * The complete model-block transform ({@link #place} then {@link #apply}) as
     * a matrix, for callers that need to inspect it — the headless parity tests,
     * chiefly. Built through a real {@link MatrixStack} so there is exactly one
     * implementation of the chain and the test can never drift from the renderer
     * by re-deriving it.
     */
    public static Matrix4f compose(TileEntityModelSettings settings)
    {
        MatrixStack matrices = new MatrixStack();

        place(matrices, settings);
        apply(matrices, settings);

        return new Matrix4f(matrices.peek().getPositionMatrix());
    }

    /**
     * Apply the settings' rotation + scale to the matrix stack (convenience
     * overload used by the BE / item renderers).
     */
    public static void apply(MatrixStack matrices, TileEntityModelSettings settings)
    {
        apply(matrices,
            settings.getOrder(),
            settings.getRx(), settings.getRy(), settings.getRz(),
            settings.isUniform(),
            settings.getSx(), settings.getSy(), settings.getSz());
    }

    /**
     * Core transform (primitive args — no world/GL state), mirroring legacy
     * {@code transform(te)} exactly.
     *
     * @param order the rotation order; only {@link RotationOrder#ZYX} takes the
     *              X→Y→Z branch, all others take Z→Y→X (legacy semantics).
     */
    public static void apply(MatrixStack matrices, RotationOrder order, float rx, float ry, float rz, boolean uniform, float sx, float sy, float sz)
    {
        if (order == RotationOrder.ZYX)
        {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
        }
        else
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        }

        /* the uniform rendering is needed for backwards compatibility
         * with model blocks that have only sx set */
        if (uniform)
        {
            matrices.scale(sx, sx, sx);
        }
        else
        {
            matrices.scale(sx, sy, sz);
        }
    }
}
