package mchorse.chameleon.lib.render;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;

/**
 * Cube renderer
 *
 * Renders given bones from the model as cubes, fully
 *
 * <h3>Port notes (1.12.2 immediate mode → 1.20.4 {@link VertexConsumer})</h3>
 *
 * <p>Positions and normals are still transformed <b>in Java</b> by Chameleon's
 * own {@link MatrixStack}, exactly as legacy did, and then handed to the
 * consumer along with the vanilla frame's position/normal matrices — which is
 * the faithful equivalent of legacy emitting model-space-transformed vertices
 * into a GL modelview that held the entity transform.</p>
 *
 * <p><b>Lightmap channel order.</b> Legacy called
 * {@code builder.lightmap(lightY, lightX)} — sky first — and that was the
 * <i>correct</i> convention on 1.12.2: {@code BufferBuilder.lightmap(sky, block)}
 * wrote its second argument into the first (block) short, the same order
 * vanilla particles used ({@code lightmap(packed >> 16, packed & 0xFFFF)}).
 * There was never a visual swap in legacy. An earlier port read the argument
 * order as a channel swap and reproduced it into {@code .light(int)}'s packing
 * (block low, sky high), which swapped the channels for real — models sampled
 * the torch column under open sky (slightly too bright and warm by day,
 * torch-bright at night). {@link #packLight} packs straight through, matching
 * the particle path's translation of the same legacy idiom
 * ({@code BedrockComponentAppearanceBillboard}). {@code absoluteBrightness}
 * (zeroes {@code lightX}) and {@code glow} (raises it towards 240) act on the
 * <b>block</b> channel, exactly as legacy's {@code lightX} did.</p>
 *
 * Legacy source: chameleon/.../lib/render/ChameleonCubeRenderer.java
 */
public class ChameleonCubeRenderer implements IChameleonRenderProcessor
{
    private float r;
    private float g;
    private float b;
    private float a;

    /* Temporary variables to avoid allocating and GC vectors */
    private Vector3f normal = new Vector3f();
    private Vector4f vertex = new Vector4f();

    /* 1.20.4 draw target, set by ChameleonRenderer before the walk */
    private net.minecraft.client.util.math.MatrixStack matrices;
    private int light;
    private int overlay;

    public void setup(net.minecraft.client.util.math.MatrixStack matrices, int light, int overlay)
    {
        this.matrices = matrices;
        this.light = light;
        this.overlay = overlay;
    }

    /**
     * Legacy's {@code OpenGlHelper.lastBrightnessX/Y} juggling, in packed form.
     * See the class javadoc for the channel order.
     *
     * <p>The two halves are unpacked with masks rather than through
     * {@link LightmapTextureManager}: its {@code getBlockLightCoordinates} /
     * {@code getSkyLightCoordinates} return the 0..15 light <b>level</b> despite
     * the name, while {@code lastBrightnessX/Y} — and the {@code 240} ceiling the
     * glow lerp targets — are the 0..240 texture <b>coordinate</b>. These masks
     * are the exact inverse of what {@code VertexConsumer.light(int)} does.</p>
     */
    private int packLight(ModelBone bone)
    {
        int lightX = this.light & 0xFFFF;
        int lightY = (this.light >> 16) & 0xFFFF;

        if (bone.absoluteBrightness)
        {
            lightX = 0;
        }

        lightX = (int) Interpolation.LINEAR.interpolate(lightX, 240, bone.glow);

        /* Block (legacy lightX) in the low half, sky in the high — .light(int)'s packing */
        return (lightX & 0xFFFF) | ((lightY & 0xFFFF) << 16);
    }

    @Override
    public boolean renderBone(VertexConsumer builder, MatrixStack stack, ModelBone bone)
    {
        this.r = bone.color.r;
        this.g = bone.color.g;
        this.b = bone.color.b;
        this.a = bone.color.a;

        int packed = this.packLight(bone);

        for (ModelCube cube : bone.cubes)
        {
            this.renderCube(builder, stack, cube, packed);
        }

        return false;
    }

    private void renderCube(VertexConsumer builder, MatrixStack stack, ModelCube cube, int packed)
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
            this.normal.set(quad.normal.x, quad.normal.y, quad.normal.z);
            stack.getNormalMatrix().transform(this.normal);

            /* For 0 sized cubes on either axis, to avoid getting dark shading on models
             * which didn't correctly setup the UV faces.
             *
             * For example two wings, first wing uses top face for texturing the flap,
             * and second wing uses bottom face as a flap. In the end, the second wing
             * will appear dark shaded without this fix.
             */
            if (this.normal.getX() < 0 && (cube.size.y == 0 || cube.size.z == 0)) this.normal.x *= -1;
            if (this.normal.getY() < 0 && (cube.size.x == 0 || cube.size.z == 0)) this.normal.y *= -1;
            if (this.normal.getZ() < 0 && (cube.size.x == 0 || cube.size.y == 0)) this.normal.z *= -1;

            for (ModelVertex vertex : quad.vertices)
            {
                this.vertex.set(vertex.position);
                this.vertex.w = 1;
                stack.getModelMatrix().transform(this.vertex);

                builder.vertex(pose, this.vertex.x, this.vertex.y, this.vertex.z)
                    .color(this.r, this.g, this.b, this.a)
                    .texture(vertex.uv.x, vertex.uv.y)
                    .overlay(this.overlay)
                    .light(packed)
                    .normal(normalMatrix, this.normal.x, this.normal.y, this.normal.z)
                    .next();
            }
        }

        stack.pop();
    }
}
