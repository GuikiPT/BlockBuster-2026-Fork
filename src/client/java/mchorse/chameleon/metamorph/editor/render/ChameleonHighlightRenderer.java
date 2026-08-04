package mchorse.chameleon.metamorph.editor.render;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;
import mchorse.chameleon.lib.render.ChameleonPostRenderer;
import mchorse.chameleon.lib.render.IChameleonRenderProcessor;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.mclib.client.Draw;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.render.RenderingUtilsClient;
import mchorse.mclib.utils.MatrixUtils;
import mchorse.mclib.utils.RenderingUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector4f;

/**
 * Highlight renderer
 *
 * This bad boy is responsible for rendering given model's bone
 * as a blue box highlight (in addition with axes identifiers)
 *
 * <h3>Port notes</h3>
 *
 * <p>Legacy set the highlight colour with {@code GlStateManager.color(0, 0.5F,
 * 1, 0.33F)} in {@code GuiChameleonModelRenderer} and drew
 * {@link VertexFormats#POSITION}-only quads. Core profile has no ambient colour,
 * so the tint moves onto the vertices here ({@link #COLOR}) and the format
 * becomes {@code POSITION_COLOR} — same pixels, one fewer piece of global
 * state.</p>
 *
 * <p>Like {@link Draw}, this bypasses the deferred vertex-consumer pipeline and
 * draws immediately through the {@link Tessellator}: the highlight is an overlay
 * gizmo drawn with depth off, and batching it with the model's geometry would
 * put it in the wrong pass. The {@code builder} argument of
 * {@link IChameleonRenderProcessor} is therefore unused (legacy passed the
 * shared tessellator buffer and called {@code begin} on it itself, which is the
 * same thing).</p>
 *
 * <p>{@code RenderingUtils.glRevertRotationScale} has no direct port; the
 * equivalent is composing the inverse rotation/scale onto the matrix stack,
 * which is what {@link #revertRotationScale} does — so the axis gizmo stays
 * world-aligned in GLOBAL orientation mode.</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/render/ChameleonHighlightRenderer.java
 */
public class ChameleonHighlightRenderer implements IChameleonRenderProcessor
{
    /** Legacy {@code GlStateManager.color(0, 0.5F, 1, 0.33F)}. */
    private static final float[] COLOR = {0F, 0.5F, 1F, 0.33F};

    private String boneName;
    private Vector4f vertex = new Vector4f();

    private net.minecraft.client.util.math.MatrixStack matrices;

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
        if (this.matrices == null || !bone.id.equals(this.boneName))
        {
            return false;
        }

        try
        {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (ModelCube cube : bone.cubes)
            {
                this.renderCubeForHighlight(buffer, stack, cube);
            }

            tessellator.draw();
        }
        catch (Exception e)
        {
            /* No GL context — the highlight simply doesn't render. */
        }

        this.matrices.push();

        try
        {
            ChameleonPostRenderer.multiplyMatrix(stack, bone, this.matrices);

            if (GuiTransformations.GuiStaticTransformOrientation.getOrientation() == GuiTransformations.TransformOrientation.GLOBAL)
            {
                Vector3d rotation = new Vector3d(bone.current.rotation);
                rotation.x = Math.toRadians(rotation.x);
                rotation.y = Math.toRadians(rotation.y);
                rotation.z = Math.toRadians(rotation.z);

                this.revertRotationScale(rotation, new Vector3d(bone.current.scale));
            }

            Draw.axis(this.matrices, 0.25F * 1.5F);
        }
        finally
        {
            this.matrices.pop();
        }

        return true;
    }

    /**
     * Legacy {@code RenderingUtils.glRevertRotationScale(rotation, scale, XYZ)}:
     * strip the bone's own rotation and scale off the frame so the axis gizmo is
     * drawn world-aligned and at a fixed size.
     */
    private void revertRotationScale(Vector3d rotation, Vector3d scale)
    {
        javax.vecmath.Matrix4f transform = MatrixUtils.getRotationMatrix(
            (float) rotation.x, (float) rotation.y, (float) rotation.z, MatrixUtils.RotationOrder.XYZ);

        transform.m00 *= scale.x;
        transform.m10 *= scale.x;
        transform.m20 *= scale.x;
        transform.m01 *= scale.y;
        transform.m11 *= scale.y;
        transform.m21 *= scale.y;
        transform.m02 *= scale.z;
        transform.m12 *= scale.z;
        transform.m22 *= scale.z;

        RenderingUtilsClient.multiply(this.matrices,
            RenderingUtils.revertRotationScale(new Matrix4d(transform)));
    }

    private void renderCubeForHighlight(BufferBuilder builder, MatrixStack stack, ModelCube cube)
    {
        Matrix4f pose = this.matrices.peek().getPositionMatrix();

        stack.push();
        stack.moveToCubePivot(cube);
        stack.rotateCube(cube);
        stack.moveBackFromCubePivot(cube);

        for (ModelQuad quad : cube.quads)
        {
            for (ModelVertex vertex : quad.vertices)
            {
                this.vertex.set(vertex.position);
                this.vertex.w = 1;
                stack.getModelMatrix().transform(this.vertex);

                builder.vertex(pose, this.vertex.getX(), this.vertex.getY(), this.vertex.getZ())
                    .color(COLOR[0], COLOR[1], COLOR[2], COLOR[3])
                    .next();
            }
        }

        stack.pop();
    }
}
