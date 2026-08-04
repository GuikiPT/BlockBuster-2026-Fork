package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import javax.vecmath.Matrix4d;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MatrixUtils;

/**
 * P137 — headless math cores extracted from the model editor tabs
 * ({@code GuiModelLimbs}). These are the load-bearing parity surface: the legacy
 * {@code fixLimbPosition}, {@code correctTransformationParenting} and the
 * anchor "move" pose-translate overwrite. The GUI classes stay thin wrappers
 * that resolve GUI/renderer state into the arguments here so the numeric
 * behavior can be golden-tested without OpenGL.
 *
 * <p>Everything is a 1:1 port of
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiModelLimbs.java}. Sign flips,
 * multiply order, the {@code 24/16} root offset and the {@code 16}× BB-coordinate
 * scaling are all load-bearing quirks — do not "simplify" them.</p>
 */
public final class ModelEditorMath
{
    private ModelEditorMath()
    {}

    /**
     * Which renderer class a limb resolves to, standing in for the legacy
     * {@code getLimbClass} class comparison so the math is GL-free.
     *
     * <ul>
     *   <li>{@link #VANILLA} — plain {@code ModelCustomRenderer}</li>
     *   <li>{@link #OBJ} — {@code ModelOBJRenderer}</li>
     *   <li>{@link #VOX} — {@code ModelVoxRenderer}</li>
     *   <li>{@link #NONE} — the compiled model has no renderer for this limb
     *   (a failed compile; legacy {@code getLimbClass} returned {@code null}
     *   here and would then have NPE'd only if {@code renderModel} itself was
     *   null)</li>
     * </ul>
     *
     * <p><b>{@code NONE} is not a synonym for {@code VANILLA}</b> (S22/P243).
     * Every legacy test on the class is written as
     * {@code clazz != ModelCustomRenderer.class}, which a {@code null} class
     * satisfies — so a rendererless limb took the <i>non-box</i> branch of
     * {@code fixLimbPosition} and was refused by the dupe/rename/remove guards.
     * An earlier port mapped {@code null} onto {@code VANILLA} with a comment
     * claiming that was legacy's branch; it is the opposite one. Kept as its own
     * constant so both consumers get legacy's answer from the single
     * {@code kind != VANILLA} test they already perform.</p>
     */
    public enum LimbKind
    {
        VANILLA, OBJ, VOX, NONE
    }

    /**
     * Port of {@code GuiModelLimbs.fixLimbPosition}. Rebuilds the limb's
     * {@code T · Rz · Ry · Rx · S} matrix and appends a compensating delta so the
     * limb stays visually put when its anchor/origin moves, writing the corrected
     * translate back into {@code transform.translate}.
     *
     * <p>Faithful notes:</p>
     * <ul>
     *   <li>The OBJ scale block uses {@code m00 = legacyObj ? 16 : -16}. In the
     *   legacy source the ternary also tested {@code getLimbClass == VOX}, but
     *   that branch was <b>dead</b> (it only ran when the class was exactly
     *   {@code ModelOBJRenderer}, and {@code ModelVoxRenderer} does not extend it)
     *   — so it always evaluated to {@code legacyObj ? 16 : -16}. Preserved.</li>
     *   <li>VOX limbs skip the {@code 16}× scale block entirely (the outer
     *   {@code clazz == ModelOBJRenderer} guard is false for VOX) and only get the
     *   OBJ-style anchor delta with its {@code lastAnchorZ - z} Z sign.</li>
     *   <li>OBJ/VOX anchor delta: {@code (x-lastX, y-lastY, lastZ-z)}.</li>
     *   <li>Vanilla path: scales by the limb {@code size} then applies the
     *   opposite-sign anchor delta {@code (lastX-x, lastY-y, lastZ-z)}.</li>
     *   <li>{@link LimbKind#NONE} follows the OBJ/VOX side of the outer
     *   {@code clazz != ModelCustomRenderer.class} test (without the 16× block,
     *   whose guard is {@code clazz == ModelOBJRenderer.class}) — legacy's
     *   answer for a {@code null} renderer class.</li>
     * </ul>
     */
    public static void fixLimbPosition(ModelTransform transform, int[] size, boolean legacyObj, LimbKind kind, float lastAnchorX, float lastAnchorY, float lastAnchorZ, float x, float y, float z)
    {
        Matrix4f mat = new Matrix4f();
        mat.setIdentity();
        mat.m03 = transform.translate[0];
        mat.m13 = transform.translate[1];
        mat.m23 = transform.translate[2];

        Matrix4f mat2 = new Matrix4f();
        mat2.rotZ((float) Math.toRadians(transform.rotate[2]));
        mat.mul(mat2);
        mat2.rotY((float) Math.toRadians(transform.rotate[1]));
        mat.mul(mat2);
        mat2.rotX((float) Math.toRadians(transform.rotate[0]));
        mat.mul(mat2);

        mat2.setIdentity();
        mat2.m00 = transform.scale[0];
        mat2.m11 = transform.scale[1];
        mat2.m22 = transform.scale[2];
        mat.mul(mat2);

        if (kind != LimbKind.VANILLA)
        {
            if (kind == LimbKind.OBJ)
            {
                mat2.setIdentity();
                /* the VOX sub-check in legacy was dead here → legacyObj ? 16 : -16 */
                mat2.m00 = legacyObj ? 16 : -16;
                mat2.m11 = 16;
                mat2.m22 = 16;
                mat.mul(mat2);
            }

            mat2.setIdentity();
            mat2.m03 = x - lastAnchorX;
            mat2.m13 = y - lastAnchorY;
            mat2.m23 = lastAnchorZ - z;
            mat.mul(mat2);
        }
        else
        {
            mat2.setIdentity();
            mat2.m00 = size[0];
            mat2.m11 = size[1];
            mat2.m22 = size[2];
            mat.mul(mat2);

            mat2.setIdentity();
            mat2.m03 = lastAnchorX - x;
            mat2.m13 = lastAnchorY - y;
            mat2.m23 = lastAnchorZ - z;
            mat.mul(mat2);
        }

        transform.translate[0] = mat.m03;
        transform.translate[1] = mat.m13;
        transform.translate[2] = mat.m23;
    }

    /** Result of {@link #computeParentCorrection}. */
    public static final class ParentCorrection
    {
        public final float[] translate;
        public final float[] rotate;
        public final float[] scale;

        public ParentCorrection(float[] translate, float[] rotate, float[] scale)
        {
            this.translate = translate;
            this.rotate = rotate;
            this.scale = scale;
        }
    }

    /**
     * Port of {@code GuiModelLimbs.correctTransformationParenting}. Computes the
     * pose transform that keeps a limb visually put after it is reparented.
     *
     * <ul>
     *   <li><b>Non-root</b> (new parent is a real limb): pass its world matrix as
     *   {@code newParentWorld} and the limb's world matrix as {@code limbWorld};
     *   {@code modelMatrix}/{@code limbModelView} are ignored. diff =
     *   {@code newParentWorld^-1 · limbWorld}.</li>
     *   <li><b>Root</b> (new parent absent): pass {@code newParentWorld == null},
     *   the viewport {@code modelMatrix} and the limb's {@code limbModelView};
     *   diff = {@code modelMatrix^-1 · limbModelView}, then {@code m13 -= 24/16}
     *   (mirrors {@code ModelCustomRenderer.applyTransform}'s 24-px body offset).</li>
     * </ul>
     *
     * <p>The rotation is transposed, translation scaled by 16 into BB coordinates,
     * and Y/Z of translation and rotation are negated — exactly as legacy.</p>
     */
    public static ParentCorrection computeParentCorrection(Matrix4d limbWorld, Matrix4d newParentWorld, Matrix4d modelMatrix, Matrix4d limbModelView)
    {
        Matrix4d diff;

        if (newParentWorld != null)
        {
            diff = new Matrix4d(newParentWorld);
            diff.invert();
            diff.mul(limbWorld);
        }
        else
        {
            diff = new Matrix4d(modelMatrix);
            diff.invert();
            diff.mul(limbModelView);
            diff.m13 -= 24 / 16F;
        }

        MatrixUtils.Transformation correction = MatrixUtils.getTransformation(diff);

        correction.rotation.transpose();

        Vector3f trans = correction.getTranslation3f();
        Vector3f rot = correction.getRotation(MatrixUtils.RotationOrder.XYZ);
        Vector3f scale = correction.getScale();

        /* everything is scaled by 1/16 → convert normal coords into BB coords */
        trans.scale(16);

        return new ParentCorrection(
            new float[] {trans.x, -trans.y, -trans.z},
            new float[] {rot.x, -rot.y, -rot.z},
            new float[] {scale.x, scale.y, scale.z});
    }

    /**
     * Port of the anchor "move" pose-translate overwrite in
     * {@code GuiModelLimbs.doSetupAnchorPoint} (move branch). Overwrites the pose
     * translate to {@code (-origin.x*16, origin.y*16, -origin.z*16)} — note the
     * X and Z sign flips, Y kept positive.
     */
    public static float[] anchorMoveTranslate(float[] origin)
    {
        return new float[] {-origin[0] * 16, origin[1] * 16, -origin[2] * 16};
    }

    /**
     * Port of the origin computation in
     * {@code GuiModelLimbs.doSetupAnchorPoint}: lerp the renderer's min/max bounds
     * by the modal's 0..1 anchor vector to get the new limb origin.
     */
    public static float[] anchorLerpOrigin(Vector3f min, Vector3f max, Vector3f anchor)
    {
        return new float[] {
            Interpolations.lerp(min.x, max.x, anchor.x),
            Interpolations.lerp(min.y, max.y, anchor.y),
            Interpolations.lerp(min.z, max.z, anchor.z)
        };
    }
}
