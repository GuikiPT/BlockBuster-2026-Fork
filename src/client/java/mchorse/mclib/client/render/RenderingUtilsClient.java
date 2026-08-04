package mchorse.mclib.client.render;

import java.util.List;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.RenderingUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;

import javax.vecmath.Matrix4d;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

/**
 * Client half of {@link RenderingUtils} (roadmap P54): the two legacy GL
 * helpers, expressed against a {@link MatrixStack}.
 *
 * <p>Legacy {@code glFacingRotation}/{@code glRevertRotationScale} read GL state
 * (the model-view matrix and the camera matrix an ASM hook captured after camera
 * setup) and multiplied a matrix onto the fixed-function stack. Neither read has
 * a core-profile equivalent, and neither is needed: 1.20.4 hands the frame's
 * matrix to the renderer, and the camera is a live object
 * ({@code gameRenderer.getCamera()}) rather than a snapshot of GL state.</p>
 *
 * <p><b>Camera space vs world space.</b> The matrix a world-render frame carries
 * maps model space to <i>camera</i> space — vanilla pushes the view rotation
 * {@code R_view} at the top of {@code WorldRenderer.render} and every entity is
 * translated by {@code entityPos - cameraPos}. The facing math needs the drawn
 * object's <b>world</b> position, so {@link #worldPosition} undoes both:
 * {@code Camera.getRotation()} is exactly {@code R_view⁻¹} (it is what vanilla
 * multiplies in to billboard a nameplate), so rotating the matrix's translation
 * column by it and adding the camera position lands back in the world. That is
 * the same quantity legacy's no-arg {@code MatrixUtils.getTransformation()}
 * produced by inverting its captured camera matrix and adding the render-view
 * entity's interpolated position.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/RenderingUtils.java
 */
public final class RenderingUtilsClient
{
    private RenderingUtilsClient()
    {}

    /**
     * The quarter-turn that 1.12.2 applied <b>inside</b> the block draw and
     * 1.20.4 does not (roadmap P289).
     *
     * <p>1.12.2's {@code BlockRendererDispatcher.renderBlockBrightness} went
     * through {@code BlockModelRenderer.renderModelBrightness}, whose <i>very
     * first statement</i> is {@code GlStateManager.rotate(90F, 0F, 1F, 0F)}.
     * Every legacy caller was written against that: they pre-rotate {@code -90°}
     * about Y, shift by {@code (-0.5, -0.5, +0.5)} and let the callee's
     * {@code +90°} turn the frame back, so the net effect is the plain
     * {@code translate(-0.5, -0.5, -0.5)} that centres a unit block on the
     * origin.</p>
     *
     * <p>1.20.4's successor, {@code BlockRenderManager.renderBlockAsEntity},
     * carries <b>no</b> rotation — verified against the loom-cache named jar's
     * bytecode: it resolves the baked model, reads the block colour and hands
     * {@code matrices.peek()} straight to {@code BlockModelRenderer.render}. When
     * Mojang moved to the matrix stack they hoisted the quarter-turn out of the
     * callee into each caller; vanilla's own {@code TntEntityRenderer} — the
     * class Metamorph's block morph was copied from — is now literally
     * {@code translate(0, 0.5, 0)} → {@code POSITIVE_Y(-90)} →
     * {@code translate(-0.5, -0.5, 0.5)} → <b>{@code POSITIVE_Y(90)}</b> →
     * {@code renderBlockAsEntity}.</p>
     *
     * <p>Porting a legacy op list verbatim therefore drops a rotation, and the
     * leftover {@code -90°} spins the centring offset onto the wrong axes: the
     * block lands a full block away on <b>X</b> (and a quarter-turn off in
     * texture orientation) while Y and Z still look right. That is exactly the
     * "block morph renders off-centre, +1 on X fixes it" report. Call this
     * immediately before every {@code renderBlockAsEntity} that stands in for a
     * legacy {@code renderBlockBrightness};
     * {@code BlockAsEntityCompensationTest} lints that no call site forgets
     * it.</p>
     */
    public static void blockBrightnessQuarterTurn(MatrixStack matrices)
    {
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
    }

    /**
     * The world position of the current frame's origin, or {@code null} when
     * there is no camera to resolve it against (headless, or before the first
     * world render). Callers treat null as "no facing/look-at data" rather than
     * throwing — the totality rule.
     *
     * <p><b>The 180° flip.</b> The entity-render view rotation is
     * {@code Rx(pitch)·Ry(yaw + 180)} ({@code GameRenderer.renderWorld}), while
     * {@code Camera.getRotation()} is {@code Ry(-yaw)·Rx(pitch)} — so the true
     * view <i>inverse</i> is {@code getRotation()·Ry(180)}, and the camera-local
     * X/Z must be negated before the quaternion is applied. Applying the bare
     * quaternion resolves the point mirrored through the camera's vertical
     * plane (a morph 5 blocks ahead reads as 5 blocks behind), and because the
     * mirror turns with the camera, the look-at facing modes over-rotated with
     * every camera turn — the reported "look-at spins too fast".</p>
     */
    public static Vector3f worldPosition(MatrixStack matrices)
    {
        Camera camera = camera();

        if (matrices == null || camera == null)
        {
            return null;
        }

        org.joml.Matrix4f m = matrices.peek().getPositionMatrix();
        org.joml.Vector3f translation = new org.joml.Vector3f(-m.m30(), m.m31(), -m.m32());

        camera.getRotation().transform(translation);

        Vec3d position = camera.getPos();

        return new Vector3f(
            (float) (position.x + translation.x),
            (float) (position.y + translation.y),
            (float) (position.z + translation.z));
    }

    /**
     * Legacy {@code glFacingRotation}: multiply the frame by the billboard
     * rotation for {@code facing}. {@code position} is the drawn object's world
     * position (see {@link #worldPosition}); it may be null for the rotate modes,
     * which do not read it. A missing camera makes this a no-op.
     */
    public static void applyFacingRotation(MatrixStack matrices, RenderingUtils.Facing facing, Vector3f position, Vector3f direction)
    {
        Camera camera = camera();

        if (matrices == null || camera == null)
        {
            return;
        }

        Vec3d pos = camera.getPos();
        Vector3f target = position == null ? new Vector3f() : position;

        multiply(matrices, RenderingUtils.facingRotation(facing, target, direction,
            camera.getYaw(), camera.getPitch(), pos.x, pos.y, pos.z));
    }

    /**
     * Legacy {@code glRevertRotationScale}: strip the parent frame's rotation
     * and scale, leaving its translation. The decomposition runs on the
     * <b>world-space</b> transform (camera rotation undone first), because that
     * is the space legacy's {@code getTransformation()} decomposed in — reverting
     * against the camera-space matrix would fold the camera's own rotation into
     * the result and the image would swing as the player looked around.
     *
     * <p>The {@code rotateY(180°)} completes the view inverse —
     * {@code Camera.getRotation()} alone is off from it by exactly that flip
     * (see {@link #worldPosition}'s note); without it the "world" matrix
     * decomposed here is conjugated by a camera-dependent half-turn and the
     * revert leaves a residual that swings with the view.</p>
     */
    public static void applyRevertRotationScale(MatrixStack matrices)
    {
        Camera camera = camera();

        if (matrices == null || camera == null)
        {
            return;
        }

        org.joml.Matrix4f world = new org.joml.Matrix4f().rotation(camera.getRotation()).rotateY((float) Math.PI).mul(matrices.peek().getPositionMatrix());

        multiply(matrices, RenderingUtils.revertRotationScale(new Matrix4d(toVecmath(world))));
    }

    /**
     * Legacy {@code renderImage(image, scale[, color])}: a double-sided unit
     * quad, drawn white unless a colour is given (P54 pass 6).
     *
     * <p>The quad is mirrored on X in the front-facing third-person
     * perspective (legacy {@code thirdPersonView == 2}), so the image is not
     * drawn backwards when the camera is in front of the player.</p>
     *
     * <p>Legacy's {@code alphaFunc(GREATER, 0)} / {@code SRC_ALPHA} blend /
     * {@code enableCull} are the picture {@link net.minecraft.client.render.RenderLayer}
     * now; the normal is the unlit {@code (0, 1, 0)} because legacy drew this
     * with fixed-function lighting off (see {@code ImageMorphRenderer.normalOf}).
     *
     * @param depth false reproduces the caller's {@code disableDepth()}
     */
    public static void renderImage(MatrixStack matrices, VertexConsumerProvider consumers, Identifier image, float scale, Color color, int light, int overlay, boolean depth)
    {
        if (matrices == null || consumers == null || image == null)
        {
            return;
        }

        VertexConsumer buffer = consumers.getBuffer(McLibRenderLayers.picture(image, false, depth));
        org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();

        float width = scale * (mirrored() ? -1.0F : 1.0F) * 0.5F;
        float height = scale * 0.5F;

        /* Frontface */
        vertex(buffer, matrix, -width, height, 0, 0, color, light, overlay);
        vertex(buffer, matrix, -width, -height, 0, 1, color, light, overlay);
        vertex(buffer, matrix, width, -height, 1, 1, color, light, overlay);
        vertex(buffer, matrix, width, height, 1, 0, color, light, overlay);

        /* Backface */
        vertex(buffer, matrix, width, height, 1, 0, color, light, overlay);
        vertex(buffer, matrix, width, -height, 1, 1, color, light, overlay);
        vertex(buffer, matrix, -width, -height, 0, 1, color, light, overlay);
        vertex(buffer, matrix, -width, height, 0, 0, color, light, overlay);
    }

    private static void vertex(VertexConsumer buffer, org.joml.Matrix4f matrix, float x, float y, float u, float v, Color color, int light, int overlay)
    {
        buffer.vertex(matrix, x, y, 0.0F)
            .color(color.r, color.g, color.b, color.a)
            .texture(u, v)
            .overlay(overlay)
            .light(light)
            .normal(0.0F, 1.0F, 0.0F)
            .next();
    }

    /* --------------------------------------------------------------------- */
    /* Lines (P219.2)                                                        */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy {@code renderCircle(center, normal, radius, divisions, color,
     * thickness)} — a solid circle, i.e. {@link #renderCircleDotted} with a
     * zero skip.
     */
    public static void renderCircle(MatrixStack matrices, VertexConsumerProvider consumers, Vector3d center, Vector3d normal, double radius, int divisions, Color color, double thickness, boolean depth)
    {
        renderCircleDotted(matrices, consumers, center, normal, radius, divisions, color, thickness, 0, depth);
    }

    /**
     * Legacy {@code renderCircleDotted(...)}: the circle's
     * {@link RenderingUtils#circleVertices vertex stream} pushed through the
     * {@code GL_LINES} layer.
     *
     * <p>Legacy set the colour once with {@code glColor4f} and emitted
     * position-only vertices; core profile has no current colour, so the same
     * colour is written per vertex instead — identical result, and the only
     * translation this method performs.</p>
     */
    public static void renderCircleDotted(MatrixStack matrices, VertexConsumerProvider consumers, Vector3d center, Vector3d normal, double radius, int divisions, Color color, double thickness, int skipDivision, boolean depth)
    {
        renderLines(matrices, consumers, RenderingUtils.circleVertices(center, normal, radius, divisions, skipDivision), color, thickness, depth);
    }

    /**
     * A raw {@code GL_LINES} vertex stream: consecutive pairs form one line,
     * exactly as GL treated them.
     *
     * @param thickness legacy {@code glLineWidth(thickness)}
     * @param depth     false reproduces the caller's {@code disableDepth()}
     */
    public static void renderLines(MatrixStack matrices, VertexConsumerProvider consumers, List<Vector3d> vertices, Color color, double thickness, boolean depth)
    {
        emit(matrices, consumers, McLibRenderLayers.lines(thickness, depth), vertices, color, 2);
    }

    /**
     * A {@code GL_LINE_STRIP} vertex stream: every consecutive pair of vertices
     * forms a line, so the run is one connected polyline.
     */
    public static void renderLineStrip(MatrixStack matrices, VertexConsumerProvider consumers, List<Vector3d> vertices, Color color, double thickness, boolean depth)
    {
        emit(matrices, consumers, McLibRenderLayers.lineStrip(thickness, depth), vertices, color, 2);
    }

    /**
     * @param minimum the vertex count below which the draw mode has nothing to
     *                draw — emitting fewer would leave a partial primitive in
     *                the buffer
     */
    private static void emit(MatrixStack matrices, VertexConsumerProvider consumers, RenderLayer layer, List<Vector3d> vertices, Color color, int minimum)
    {
        if (matrices == null || consumers == null || vertices == null || vertices.size() < minimum)
        {
            return;
        }

        VertexConsumer buffer = consumers.getBuffer(layer);
        org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (Vector3d vertex : vertices)
        {
            /* POSITION_COLOR — position then colour, in that order. */
            buffer.vertex(matrix, (float) vertex.x, (float) vertex.y, (float) vertex.z)
                .color(color.r, color.g, color.b, color.a)
                .next();
        }
    }

    /**
     * Legacy {@code gameSettings.thirdPersonView == 2} — the front-facing
     * third-person camera, the one perspective the image quads mirror in.
     */
    public static boolean mirrored()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc != null && mc.options != null && mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT;
    }

    /**
     * Multiply a vecmath matrix into both halves of the frame. The normal matrix
     * has to be multiplied by hand: {@link MatrixStack#multiplyPositionMatrix}
     * deliberately touches only the position matrix (vanilla uses it for
     * projection-like matrices), and a facing rotation that reached the geometry
     * but not the normals would leave the lighting pointing the old way.
     */
    public static void multiply(MatrixStack matrices, Matrix4f matrix)
    {
        org.joml.Matrix4f joml = toJoml(matrix);

        matrices.multiplyPositionMatrix(joml);
        matrices.peek().getNormalMatrix().mul(new Matrix3f(joml));
    }

    /** joml (column-major fields) → vecmath (row-major fields), same maths. */
    public static Matrix4f toVecmath(org.joml.Matrix4f m)
    {
        return new Matrix4f(
            m.m00(), m.m10(), m.m20(), m.m30(),
            m.m01(), m.m11(), m.m21(), m.m31(),
            m.m02(), m.m12(), m.m22(), m.m32(),
            m.m03(), m.m13(), m.m23(), m.m33());
    }

    /** vecmath (row-major fields) → joml (column-major fields), same maths. */
    public static org.joml.Matrix4f toJoml(Matrix4f m)
    {
        return new org.joml.Matrix4f(
            m.m00, m.m10, m.m20, m.m30,
            m.m01, m.m11, m.m21, m.m31,
            m.m02, m.m12, m.m22, m.m32,
            m.m03, m.m13, m.m23, m.m33);
    }

    /**
     * The render camera, or null headlessly / before the first frame. Legacy read
     * {@code Minecraft.getRenderViewEntity()} here, which is the same entity the
     * camera follows — but the camera also carries the third-person offset and
     * the eye height already applied, so it is the closer equivalent.
     */
    private static Camera camera()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.gameRenderer == null)
        {
            return null;
        }

        Camera camera = mc.gameRenderer.getCamera();

        return camera != null && camera.isReady() ? camera : null;
    }
}
