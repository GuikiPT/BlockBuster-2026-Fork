package mchorse.chameleon.metamorph.editor.render;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;
import mchorse.chameleon.lib.render.IChameleonRenderProcessor;
import mchorse.chameleon.lib.utils.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import javax.vecmath.Vector4f;

/**
 * Stencil render processor
 *
 * This bad boy is responsible for rendering given GeoModel for
 * stencil limb picking
 *
 * <p>Each bone writes its <b>index in {@link mchorse.chameleon.lib.ChameleonModel#getBoneNames()}
 * plus one</b> into the stencil buffer, so 0 stays "nothing here" and
 * {@code GuiChameleonModelRenderer.getStencilValue} can invert the mapping.</p>
 *
 * <h3>Port note — the flush seam</h3>
 *
 * <p>{@code glStencilFunc} takes effect at <i>draw</i> time, but 1.20.4 defers
 * vertex emission into a shared buffer that is drawn later. Setting the func and
 * emitting, bone after bone, would rasterise every bone under whichever func was
 * set last — picking would always resolve to one bone. So each bone first calls
 * {@link StencilConsumers#flushAndGet()}, which draws everything queued so far
 * (under the <i>previous</i> bone's func) and hands back a fresh consumer, and
 * only then sets its own func. The caller draws once more after the walk. This
 * is the same seam {@code ModelCustomRenderer.StencilConsumers} uses for
 * Blockbuster's model editor (P84/P286).</p>
 *
 * Legacy source: chameleon/.../metamorph/editor/render/ChameleonStencilRenderer.java
 */
public class ChameleonStencilRenderer implements IChameleonRenderProcessor
{
    /**
     * Per-bone vertex-consumer seam. Implementations flush every vertex emitted
     * so far to the framebuffer and hand back a fresh consumer for the next bone.
     */
    public interface StencilConsumers
    {
        VertexConsumer flushAndGet();
    }

    private List<String> bones;
    private StencilConsumers consumers;
    private net.minecraft.client.util.math.MatrixStack matrices;
    private int light;
    private int overlay;

    private Vector4f vertex = new Vector4f();
    private float r;
    private float g;
    private float b;
    private float a;

    public void setBones(List<String> bones)
    {
        this.bones = bones;
    }

    public void setup(StencilConsumers consumers, net.minecraft.client.util.math.MatrixStack matrices, int light, int overlay)
    {
        this.consumers = consumers;
        this.matrices = matrices;
        this.light = light;
        this.overlay = overlay;
    }

    @Override
    public boolean renderBone(VertexConsumer builder, MatrixStack stack, ModelBone bone)
    {
        if (this.consumers == null || this.bones == null)
        {
            return false;
        }

        this.r = bone.color.r;
        this.g = bone.color.g;
        this.b = bone.color.b;
        this.a = bone.color.a;

        /* Draw the previous bone's geometry under its own stencil index before
         * changing it — see the class javadoc. */
        VertexConsumer consumer = this.consumers.flushAndGet();

        this.stencilFunc(this.bones.indexOf(bone.id) + 1);

        for (ModelCube cube : bone.cubes)
        {
            this.renderCube(consumer, stack, cube);
        }

        return false;
    }

    /** GL-free headlessly: picking simply yields nothing without a context. */
    private void stencilFunc(int index)
    {
        try
        {
            GL11.glStencilFunc(GL11.GL_ALWAYS, index, -1);
        }
        catch (Throwable t)
        {
            /* No GL context. */
        }
    }

    private void renderCube(VertexConsumer builder, MatrixStack stack, ModelCube cube)
    {
        net.minecraft.client.util.math.MatrixStack.Entry entry = this.matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

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

                /* Legacy's format was POSITION_TEX_COLOR; the 1.20.4 cutout
                 * layer wants the full entity format, and the extra channels are
                 * inert for a pass that only writes stencil. The UV is not: the
                 * cutout shader's discard is what keeps fully transparent texels
                 * unpickable, which legacy got from the fixed-function alpha
                 * test. */
                builder.vertex(pose, this.vertex.x, this.vertex.y, this.vertex.z)
                    .color(this.r, this.g, this.b, this.a)
                    .texture(vertex.uv.x, vertex.uv.y)
                    .overlay(this.overlay)
                    .light(this.light)
                    .normal(normalMatrix, quad.normal.x, quad.normal.y, quad.normal.z)
                    .next();
            }
        }

        stack.pop();
    }
}
