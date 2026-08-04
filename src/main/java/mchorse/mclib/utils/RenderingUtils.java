package mchorse.mclib.utils;

import java.util.ArrayList;
import java.util.List;
import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Matrix4f;
import javax.vecmath.SingularMatrixException;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * McLib rendering utilities (roadmap S1 P13/P15 utils port).
 *
 * <p>The {@link Facing} enum is the data-layer contract consumed by
 * {@code ImageMorph} (P159) and the other billboarded morphs. Alongside it live
 * the <b>pure</b> halves of the legacy GL helpers — {@link #facingRotation} for
 * {@code glFacingRotation} and {@link #revertRotationScale} for
 * {@code glRevertRotationScale} (P54). Each returns the matrix to multiply into
 * the current frame; the client half
 * ({@code mchorse.mclib.client.render.RenderingUtilsClient}) reads the camera
 * and applies it to a {@code MatrixStack}.</p>
 *
 * <p><b>The transpose.</b> Legacy handed both matrices to
 * {@code GL11.glMultMatrix} through {@code MatrixUtils.matrixToFloatBuffer},
 * which writes the vecmath matrix <i>row by row</i> into a buffer OpenGL reads
 * <i>column by column</i> — so what GL actually multiplied was the
 * <b>transpose</b> of the computed matrix, and every visual in 2.7.2 was tuned
 * against that. Both methods here therefore return the transposed (i.e.
 * as-applied) matrix, and the transpose is load-bearing rather than a bug: for
 * {@link #facingRotation} it turns the composed camera rotation into its
 * inverse, which is what makes a billboard face the camera, and for
 * {@link #revertRotationScale} it flips {@code R · S⁻¹} into {@code S⁻¹ · R⁻¹},
 * the only order that actually strips the parent's rotation and scale.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/RenderingUtils.java
 */
public class RenderingUtils
{
    /**
     * Billboard facing mode. The {@code id} strings are a disk/JSON contract;
     * {@link #fromString} returns {@code null} on any unknown id (callers fall
     * back to {@link #ROTATE_XYZ} — total reader).
     */
    public enum Facing
    {
        ROTATE_XYZ("rotate_xyz"),
        ROTATE_Y("rotate_y"),
        LOOKAT_XYZ("lookat_xyz", true, false),
        LOOKAT_Y("lookat_y", true, false),
        LOOKAT_DIRECTION("lookat_direction", true, true);

        public final String id;
        public final boolean isLookAt;
        public final boolean isDirection;

        public static Facing fromString(String string)
        {
            for (Facing facing : values())
            {
                if (facing.id.equals(string))
                {
                    return facing;
                }
            }

            return null;
        }

        Facing(String id, boolean isLookAt, boolean isDirection)
        {
            this.id = id;
            this.isLookAt = isLookAt;
            this.isDirection = isDirection;
        }

        Facing(String id)
        {
            this(id, false, false);
        }
    }

    /* --------------------------------------------------------------------- */
    /* Billboard / facing math (pure — headless-testable)                    */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code getFacingRotation}, ported whole, returning the matrix
     * <b>as GL received it</b> (see the class note on the transpose).
     *
     * <p>{@code position} is the drawn object's world position and is only read
     * by the look-at modes; the rotate modes use the camera's own angles.
     * {@code direction} is only read by {@link Facing#LOOKAT_DIRECTION} (and
     * must be non-null for it, as legacy insisted). The camera arguments are the
     * interpolated render-view values: {@code cameraYaw}/{@code cameraPitch} in
     * degrees exactly as the entity/camera reports them (the legacy negation of
     * the yaw happens inside), and {@code cameraX/Y/Z} the <b>eye</b> position
     * (1.12.2 added {@code getEyeHeight()} by hand; 1.20.4's {@code Camera.getPos()}
     * already is the eye).</p>
     */
    public static Matrix4f facingRotation(Facing facing, Vector3f position, Vector3f direction, float cameraYaw, float cameraPitch, double cameraX, double cameraY, double cameraZ)
    {
        if (facing.isDirection && direction == null)
        {
            throw new IllegalArgumentException("Argument direction cannot be null when the facing mode has isDirection=true");
        }

        Matrix4f transform = new Matrix4f();
        Matrix4f rotation = new Matrix4f();

        transform.setIdentity();

        float cYaw = -cameraYaw;
        float cPitch = cameraPitch;

        if (facing.isLookAt && !facing.isDirection)
        {
            double dX = cameraX - position.x;
            double dY = cameraY - position.y;
            double dZ = cameraZ - position.z;
            double horizontalDistance = Math.sqrt(dX * dX + dZ * dZ);

            cYaw = 180 - (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F);
            cPitch = (float) (Math.toDegrees(Math.atan2(dY, horizontalDistance)));
        }

        /* Legacy normalised the caller's vector in place; this copy keeps the
         * caller's vector untouched (ParticleMorph reuses its direction field
         * across frames), the normalised value is identical either way. */
        Vector3f dir = direction == null ? null : new Vector3f(direction);

        if (facing.isDirection)
        {
            double lengthSq = dir.lengthSquared();

            if (lengthSq < 0.0001)
            {
                dir.set(1, 0, 0);
            }
            else if (Math.abs(lengthSq - 1) > 0.0001)
            {
                dir.normalize();
            }
        }

        switch (facing)
        {
            case LOOKAT_XYZ:
            case ROTATE_XYZ:
                rotation.rotX((float) Math.toRadians(cPitch));
                transform.mul(rotation);
                rotation.rotY((float) Math.toRadians(180 - cYaw));
                transform.mul(rotation);
                break;
            case ROTATE_Y:
            case LOOKAT_Y:
                rotation.rotY((float) Math.toRadians(180 - cYaw));
                transform.mul(rotation);
                break;
            case LOOKAT_DIRECTION:
                rotation.setIdentity();
                rotation.rotY((float) Math.toRadians(getYaw(dir)));
                transform.mul(rotation);
                rotation.rotX((float) Math.toRadians(getPitch(dir) + 90));
                transform.mul(rotation);

                Vector3f cameraDir = new Vector3f(
                    (float) (cameraX - position.x),
                    (float) (cameraY - position.y),
                    (float) (cameraZ - position.z));

                Vector3f rotatedNormal = new Vector3f(0, 0, 1);

                transform.transform(rotatedNormal);

                /*
                 * The direction vector is the normal of the plane used for calculating the rotation around local y Axis.
                 * Project the cameraDir onto that plane to find out the axis angle (direction vector is the y axis).
                 */
                Vector3f projectDir = new Vector3f(dir);
                projectDir.scale(cameraDir.dot(dir));
                cameraDir.sub(projectDir);

                if (cameraDir.lengthSquared() < 1.0e-30) break;

                cameraDir.normalize();

                /*
                 * The angle between two vectors is only between 0 and 180 degrees.
                 * RotationDirection will be parallel to direction but pointing in different directions depending
                 * on the rotation of cameraDir. Use this to find out the sign of the angle
                 * between cameraDir and the rotatedNormal.
                 */
                Vector3f rotationDirection = new Vector3f();
                rotationDirection.cross(cameraDir, rotatedNormal);

                rotation.rotY(-Math.copySign(cameraDir.angle(rotatedNormal), rotationDirection.dot(dir)));
                transform.mul(rotation);
                break;
        }

        transform.transpose();

        return transform;
    }

    /**
     * Legacy {@code getRevertRotationScale}, returning the matrix <b>as GL
     * received it</b>: {@code S⁻¹ · R⁻¹} for the parent rotation {@code R} and
     * scale {@code S} decomposed out of {@code worldTransform} — post-multiplying
     * it onto a frame {@code T · R · S} leaves {@code T}, which is what "remove
     * the parent's scale and rotation" means.
     *
     * <p>Note {@link MatrixUtils#getTransformation(Matrix4d)} fills its rotation
     * matrix <i>row from column</i>, so what it returns is already {@code R⁻¹};
     * legacy inverted that back to {@code R} and multiplied {@code R · S⁻¹},
     * which the GL transpose then flipped into the correct order. Both steps are
     * preserved here. A singular rotation leaves the inversion out, exactly as
     * legacy swallowed the {@code SingularMatrixException}.</p>
     */
    public static Matrix4f revertRotationScale(Matrix4d worldTransform)
    {
        MatrixUtils.Transformation transformation = MatrixUtils.getTransformation(worldTransform);

        Matrix4d invertRotScale = new Matrix4d();

        invertRotScale.setIdentity();

        invertRotScale.m00 = transformation.scale.m00 != 0 ? 1 / transformation.scale.m00 : 0;
        invertRotScale.m11 = transformation.scale.m11 != 0 ? 1 / transformation.scale.m11 : 0;
        invertRotScale.m22 = transformation.scale.m22 != 0 ? 1 / transformation.scale.m22 : 0;

        Matrix4d rotation = new Matrix4d(transformation.rotation);

        try
        {
            rotation.invert();
        }
        catch (SingularMatrixException e)
        { }

        invertRotScale.mul(rotation, invertRotScale);

        Matrix4f result = new Matrix4f(invertRotScale);

        result.transpose();

        return result;
    }

    /**
     * The pure half of legacy {@code renderCircle}/{@code renderCircleDotted}
     * (P219.2): the {@code GL_LINES} vertex stream of a circle of
     * {@code radius} around {@code center}, lying in the plane whose normal is
     * {@code normal}.
     *
     * <p>Emission is legacy's verbatim: the circle is built in the XY plane and
     * rotated by {@code rotY(getYaw(normal)) · rotX(getPitch(normal))} — note
     * that this is a yaw/pitch pair and carries no roll, so the circle's
     * <i>seam</i> (where {@code i == divisions} meets {@code i == 1}) sits
     * wherever that composition puts it. The loop runs {@code i = 1..divisions}
     * stepping by {@code skipDivision + 1} and emits the pair
     * {@code (angle(i - 1), angle(i))}, which is what makes a non-zero
     * {@code skipDivision} a <b>dotted</b> circle rather than a coarse one: the
     * skipped divisions leave gaps, they do not lengthen the dashes.</p>
     *
     * <p>Totality: legacy divided by {@code divisions} unguarded and looped
     * forever on a negative {@code skipDivision}; both are clamped here (a
     * non-positive division count yields no vertices).</p>
     *
     * @param skipDivision 0 for a solid circle, n to skip n divisions after
     *                     every emitted dash
     */
    public static List<Vector3d> circleVertices(Vector3d center, Vector3d normal, double radius, int divisions, int skipDivision)
    {
        List<Vector3d> vertices = new ArrayList<Vector3d>();

        if (divisions <= 0)
        {
            return vertices;
        }

        Matrix3d rotation = new Matrix3d();
        Matrix3d transform = new Matrix3d();

        rotation.setIdentity();
        transform.rotY(Math.toRadians(getYaw(normal)));
        rotation.mul(transform);
        transform.rotX(Math.toRadians(getPitch(normal)));
        rotation.mul(transform);

        int step = Math.max(1, skipDivision + 1);

        for (int i = 1; i <= divisions; i += step)
        {
            double angle0 = 2 * Math.PI / divisions * (i - 1);
            double angle1 = 2 * Math.PI / divisions * i;

            Vector3d a = new Vector3d(radius * Math.cos(angle0), radius * Math.sin(angle0), 0);
            Vector3d b = new Vector3d(radius * Math.cos(angle1), radius * Math.sin(angle1), 0);

            rotation.transform(a);
            rotation.transform(b);

            a.add(center);
            b.add(center);

            vertices.add(a);
            vertices.add(b);
        }

        return vertices;
    }

    public static float getYaw(Vector3f direction)
    {
        return (float) getYaw(new Vector3d(direction));
    }

    public static float getPitch(Vector3f direction)
    {
        return (float) getPitch(new Vector3d(direction));
    }

    public static double getYaw(Vector3d direction)
    {
        double yaw = Math.atan2(-direction.x, direction.z);

        yaw = Math.toDegrees(yaw);

        if (yaw < -180)
        {
            yaw += 360;
        }
        else if (yaw > 180)
        {
            yaw -= 360;
        }

        return -yaw;
    }

    public static double getPitch(Vector3d direction)
    {
        double pitch = Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z));

        return -Math.toDegrees(pitch);
    }
}
