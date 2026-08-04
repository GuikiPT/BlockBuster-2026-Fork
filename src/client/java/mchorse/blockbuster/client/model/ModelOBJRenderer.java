package mchorse.blockbuster.client.model;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.obj.MeshOBJ;
import mchorse.blockbuster.api.formats.obj.MeshesOBJ;
import mchorse.blockbuster.api.formats.obj.OBJMaterial;
import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.blockbuster.client.textures.MaterialTextures;
import mchorse.blockbuster.client.textures.NativeImages;
import mchorse.blockbuster.client.textures.TextureRegistry;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OBJ limb renderer (roadmap P77).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/model/ModelOBJRenderer}. OBJ
 * meshes become limb geometry with per-group materials (textured, or
 * solid-colored via a generated {@code count×1} atlas), plus shape keys (morph
 * targets) re-tessellated per frame. The CPU-side vertex math — origin
 * subtraction, the {@code legacyObj} X-flip, the Y/normal-Y negation, the
 * solid-color atlas UV assignment and the shape-key blend (including its
 * normal-lerp no-op bug) — is separated from GL so the P85 probes test it
 * headlessly.</p>
 */
public class ModelOBJRenderer extends ModelCustomRenderer
{
    /**
     * Serial for the generated solid-colour strip identifiers. Legacy held a
     * raw {@code glGenTextures} id per renderer; a 1.20.4 texture needs an
     * {@link Identifier} to be reachable from a {@link net.minecraft.client.render.RenderLayer},
     * and it has to be per-renderer for the same reason legacy's was: two OBJ
     * limbs in one frame have different colour strips.
     */
    private static final AtomicInteger SOLID_COLOR_SERIAL = new AtomicInteger();

    public MeshesOBJ mesh;
    public OBJDisplayList[] displayLists;

    /** Morph-supplied material name → texture overrides. */
    public Map<String, ResourceLocation> materials;
    public List<ShapeKey> shapes;

    /**
     * The generated {@code count×1} solid-colour strip (legacy
     * {@code solidColorTex}), or {@code null} when this limb has no solid-colour
     * material or no client to upload it to.
     */
    public Identifier solidColorTex;

    public ModelOBJRenderer(ModelCustom model, ModelLimb limb, ModelTransform transform, MeshesOBJ mesh)
    {
        super(model, limb, transform);

        this.mesh = mesh;
        this.min = mesh.getMin();
        this.max = mesh.getMax();
    }

    /**
     * Number of solid-color (non-textured) material groups — the width of the
     * generated color atlas.
     */
    public int solidColorCount()
    {
        int count = 0;

        for (MeshOBJ mesh : this.mesh.meshes)
        {
            count += mesh.material != null && !mesh.material.useTexture ? 1 : 0;
        }

        return count;
    }

    /**
     * Solid-color atlas U coordinate for the {@code j}-th solid group. NEAREST
     * sampling on a {@code count×1} strip: {@code (j + 0.5) / count}.
     */
    public static float atlasU(int j, int count)
    {
        return (j + 0.5F) / count;
    }

    /**
     * Resolve the texture a textured group draws with, honoring morph material
     * overrides ({@link #materials}).
     */
    public static ResourceLocation resolveTexture(OBJMaterial material, Map<String, ResourceLocation> overrides)
    {
        if (material == null)
        {
            return null;
        }

        if (overrides != null && overrides.containsKey(material.name))
        {
            return overrides.get(material.name);
        }

        return material.texture;
    }

    /**
     * The generated solid-colour strip's pixels: one opaque ARGB texel per
     * non-textured material group, in mesh order — the same order
     * {@link #atlasU(int, int)} samples. Legacy wrote
     * {@code (byte) (channel * 255)} straight into an RGBA buffer; the
     * {@code (int)} truncation here is the identical cast.
     */
    public int[] solidColorPixels()
    {
        int count = this.solidColorCount();

        if (count <= 0 || this.mesh == null)
        {
            return new int[0];
        }

        int[] pixels = new int[count];
        int j = 0;

        for (MeshOBJ mesh : this.mesh.meshes)
        {
            OBJMaterial material = mesh.material;

            if (material != null && !material.useTexture)
            {
                int r = (int) (material.r * 255);
                int g = (int) (material.g * 255);
                int b = (int) (material.b * 255);

                pixels[j++] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        return pixels;
    }

    /**
     * Upload the solid-colour strip and force every textured material to a
     * {@link mchorse.blockbuster.client.textures.MipmapTexture} — legacy did
     * both inside {@code compileDisplayList}, which is this method's call site
     * too (the folder side effect and the strip must exist before the first
     * draw). No-op without a client, so headless bakes stay GL-free.
     */
    protected void setupTextures()
    {
        if (this.mesh == null || MinecraftClient.getInstance() == null)
        {
            return;
        }

        for (MeshOBJ mesh : this.mesh.meshes)
        {
            OBJMaterial material = mesh.material;

            if (material != null && material.useTexture && material.texture != null)
            {
                MaterialTextures.setup(material);
            }
        }

        int[] pixels = this.solidColorPixels();

        if (pixels.length == 0)
        {
            return;
        }

        try
        {
            Identifier id = this.solidColorTex != null
                ? this.solidColorTex
                : new Identifier("blockbuster", "textures/dynamic_obj_" + SOLID_COLOR_SERIAL.getAndIncrement());

            TextureRegistry.get().register(id, new NativeImageBackedTexture(NativeImages.fromArgb(pixels, pixels.length, 1)));

            this.solidColorTex = id;
        }
        catch (Throwable t)
        {
            /* Total: a strip that will not upload just means the solid-colour
             * groups draw with the model's own skin, never a crash. */
            Blockbuster.LOGGER.warn("Failed to upload the OBJ solid-colour strip", t);
        }
    }

    @Override
    public void bake()
    {
        int count = this.solidColorCount();
        boolean retain = this.mesh.shapes != null;

        this.setupTextures();

        this.displayLists = new OBJDisplayList[this.mesh.meshes.size()];

        int index = 0;
        int j = 0;

        for (MeshOBJ mesh : this.mesh.meshes)
        {
            boolean hasColor = mesh.material != null && !mesh.material.useTexture;
            float texF = hasColor ? atlasU(j, count) : 0;

            this.displayLists[index] = new OBJDisplayList(index, mesh, hasColor, texF, retain);
            index++;
            j += hasColor ? 1 : 0;
        }

        this.compiled = true;

        /* Discard the mesh ONLY if there are no shapes (memory). */
        if (this.mesh.shapes == null)
        {
            this.mesh = null;
        }
    }

    /**
     * Apply the legacy OBJ vertex transform to one vertex.
     *
     * @return {@code [x, y, z, u, v, nx, ny, nz]}
     */
    public float[] transformVertex(float px, float py, float pz, float u, float v, float nx, float ny, float nz, boolean legacyObj)
    {
        float ox = this.limb.origin[0];
        float oy = this.limb.origin[1];
        float oz = this.limb.origin[2];

        float x = px - ox;
        float y = -py + oy;
        float z = pz - oz;
        float rny = -ny;

        if (!legacyObj)
        {
            x = -px + ox;
            nx *= -1;
        }

        return new float[] {x, y, z, u, v, nx, rny, nz};
    }

    /**
     * The texture a material group draws with (S7/P91.1) — solid-colour groups
     * sample the generated strip, textured groups their material texture (or
     * the morph's override for that material <b>name</b>), everything else
     * {@code null} meaning "the model's own skin".
     *
     * <p><b>Legacy quirk:</b> the override map is consulted only after
     * {@code list.material.texture != null} — an override on a material that
     * never had a texture of its own is ignored, exactly as in
     * {@code renderDisplayList}.</p>
     */
    public Identifier groupTexture(OBJDisplayList list)
    {
        if (list == null)
        {
            return null;
        }

        if (list.hasColor)
        {
            return this.solidColorTex;
        }

        OBJMaterial material = list.material;

        if (material == null || !material.useTexture || material.texture == null)
        {
            return null;
        }

        ResourceLocation texture = resolveTexture(material, this.materials);

        return texture == null ? null : texture.toIdentifier();
    }

    @Override
    protected void emit(MatrixStack matrices, VertexConsumer consumer, float scale, float r, float g, float b, float a, int light, int overlay)
    {
        this.emit(matrices, consumer, MaterialTextures.ambient(), r, g, b, a, light, overlay);
    }

    /**
     * Emit every material group, each into the buffer its own texture belongs
     * to (S7/P91.1).
     *
     * <p>Legacy bound the group's texture, drew the group's display list, then
     * called {@code RenderCustomModel.bindLastTexture()} to put the entity skin
     * back. Here the "bind" is the choice of {@link VertexConsumer}, so the
     * restore is structural: nothing global changed, the next group picks its
     * own buffer and a group with no texture of its own lands in {@code
     * consumer} — the model's skin — which is precisely what the legacy restore
     * achieved.</p>
     */
    public void emit(MatrixStack matrices, VertexConsumer consumer, MaterialTextures.Consumers consumers, float r, float g, float b, float a, int light, int overlay)
    {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();
        boolean legacyObj = this.model.model.legacyObj;

        for (OBJDisplayList list : this.displayLists)
        {
            List<float[]> verts = list.bake(this, legacyObj);
            VertexConsumer target = MaterialTextures.pick(consumers, this.groupTexture(list), consumer);

            for (int i = 0; i < verts.size(); i += 3)
            {
                emitVertex(target, entry, pose, verts.get(i), r, g, b, a, light, overlay);
                emitVertex(target, entry, pose, verts.get(i + 1), r, g, b, a, light, overlay);
                emitVertex(target, entry, pose, verts.get(i + 2), r, g, b, a, light, overlay);
                emitVertex(target, entry, pose, verts.get(i + 2), r, g, b, a, light, overlay);
            }
        }
    }

    private static void emitVertex(VertexConsumer consumer, MatrixStack.Entry entry, Matrix4f pose, float[] v, float r, float g, float b, float a, int light, int overlay)
    {
        consumer.vertex(pose, v[0], v[1], v[2])
            .color(r, g, b, a)
            .texture(v[3], v[4])
            .overlay(overlay)
            .light(light)
            .normal(entry.getNormalMatrix(), v[5], v[6], v[7])
            .next();
    }

    @Override
    public void delete()
    {
        super.delete();

        this.displayLists = null;

        /* Legacy: glDeleteTextures(solidColorTex). */
        if (this.solidColorTex != null)
        {
            try
            {
                if (MinecraftClient.getInstance() != null)
                {
                    TextureRegistry.get().delete(this.solidColorTex);
                }
            }
            catch (Throwable t)
            {
                /* Total: nothing about tearing a limb down may throw. */
            }

            this.solidColorTex = null;
        }
    }

    /**
     * One material group's baked geometry. When shape keys are present the base
     * mesh + a scratch {@code temporary} are retained so the group can be
     * re-tessellated each frame.
     */
    public static class OBJDisplayList
    {
        public int index;
        public OBJMaterial material;
        public boolean hasColor;
        public float texF;

        private MeshOBJ mesh;
        private MeshOBJ temporary;

        public OBJDisplayList(int index, MeshOBJ mesh, boolean hasColor, float texF, boolean retain)
        {
            this.index = index;
            this.material = mesh.material;
            this.hasColor = hasColor;
            this.texF = texF;

            if (retain)
            {
                this.mesh = mesh;
                this.temporary = new MeshOBJ(new float[mesh.posData.length], new float[mesh.texData.length], new float[mesh.normData.length]);
            }
            else
            {
                /* No shapes: keep the base mesh for a single static bake. */
                this.mesh = mesh;
            }
        }

        /**
         * Produce the transformed triangle-soup vertices for this group. When
         * the owning renderer has active shape keys and this group is retained,
         * the shape-key blend runs first ({@link #computeTemporary}); otherwise
         * the base mesh is transformed directly.
         */
        public List<float[]> bake(ModelOBJRenderer renderer, boolean legacyObj)
        {
            MeshOBJ source = this.mesh;

            if (renderer.shapes != null && !renderer.shapes.isEmpty() && this.temporary != null && renderer.mesh != null)
            {
                source = this.computeTemporary(renderer.shapes, renderer.mesh.shapes);
            }

            List<float[]> out = new ArrayList<float[]>(source.triangles);

            for (int i = 0, c = source.triangles; i < c; i++)
            {
                float u = this.hasColor ? this.texF : source.texData[i * 2];
                float v = this.hasColor ? 0.5F : source.texData[i * 2 + 1];

                out.add(renderer.transformVertex(
                    source.posData[i * 3], source.posData[i * 3 + 1], source.posData[i * 3 + 2],
                    u, v,
                    source.normData[i * 3], source.normData[i * 3 + 1], source.normData[i * 3 + 2],
                    legacyObj));
            }

            return out;
        }

        /**
         * Run the shape-key blend into {@link #temporary} and return it.
         * <b>Bug-for-bug</b>: relative keys add {@code lerp(initial, target, f)
         * - initial}, absolute keys {@code lerp(temporary, target, f)}; the
         * normal lerp only fires inside an {@code if (nx == mesh.normData[..])}
         * guard comparing against the very values it would lerp to — a no-op.
         * Groups whose triangle counts mismatch are skipped silently.
         */
        public MeshOBJ computeTemporary(List<ShapeKey> shapes, Map<String, List<MeshOBJ>> allShapes)
        {
            for (int i = 0, c = this.mesh.triangles; i < c; i++)
            {
                this.temporary.posData[i * 3] = this.mesh.posData[i * 3];
                this.temporary.posData[i * 3 + 1] = this.mesh.posData[i * 3 + 1];
                this.temporary.posData[i * 3 + 2] = this.mesh.posData[i * 3 + 2];
                this.temporary.texData[i * 2] = this.mesh.texData[i * 2];
                this.temporary.texData[i * 2 + 1] = this.mesh.texData[i * 2 + 1];
                this.temporary.normData[i * 3] = this.mesh.normData[i * 3];
                this.temporary.normData[i * 3 + 1] = this.mesh.normData[i * 3 + 1];
                this.temporary.normData[i * 3 + 2] = this.mesh.normData[i * 3 + 2];
            }

            for (ShapeKey key : shapes)
            {
                List<MeshOBJ> list = allShapes.get(key.name);

                if (list == null)
                {
                    continue;
                }

                MeshOBJ mesh = list.get(this.index);
                float factor = key.value;

                if (mesh == null || this.temporary.triangles != mesh.triangles)
                {
                    continue;
                }

                for (int i = 0, c = this.temporary.triangles; i < c; i++)
                {
                    float x;
                    float y;
                    float z;
                    float u;
                    float v;
                    float nx = this.temporary.normData[i * 3];
                    float ny = this.temporary.normData[i * 3 + 1];
                    float nz = this.temporary.normData[i * 3 + 2];

                    if (key.relative)
                    {
                        x = this.temporary.posData[i * 3] + Interpolations.lerp(this.mesh.posData[i * 3], mesh.posData[i * 3], factor) - this.mesh.posData[i * 3];
                        y = this.temporary.posData[i * 3 + 1] + Interpolations.lerp(this.mesh.posData[i * 3 + 1], mesh.posData[i * 3 + 1], factor) - this.mesh.posData[i * 3 + 1];
                        z = this.temporary.posData[i * 3 + 2] + Interpolations.lerp(this.mesh.posData[i * 3 + 2], mesh.posData[i * 3 + 2], factor) - this.mesh.posData[i * 3 + 2];
                        u = this.temporary.texData[i * 2] + Interpolations.lerp(this.mesh.texData[i * 2], mesh.texData[i * 2], factor) - this.mesh.texData[i * 2];
                        v = this.temporary.texData[i * 2 + 1] + Interpolations.lerp(this.mesh.texData[i * 2 + 1], mesh.texData[i * 2 + 1], factor) - this.mesh.texData[i * 2 + 1];
                    }
                    else
                    {
                        x = Interpolations.lerp(this.temporary.posData[i * 3], mesh.posData[i * 3], factor);
                        y = Interpolations.lerp(this.temporary.posData[i * 3 + 1], mesh.posData[i * 3 + 1], factor);
                        z = Interpolations.lerp(this.temporary.posData[i * 3 + 2], mesh.posData[i * 3 + 2], factor);
                        u = Interpolations.lerp(this.temporary.texData[i * 2], mesh.texData[i * 2], factor);
                        v = Interpolations.lerp(this.temporary.texData[i * 2 + 1], mesh.texData[i * 2 + 1], factor);
                    }

                    if (
                        nx == mesh.normData[i * 3] &&
                        ny == mesh.normData[i * 3 + 1] &&
                        nz == mesh.normData[i * 3 + 2]
                    ) {
                        nx = Interpolations.lerp(nx, mesh.normData[i * 3], factor);
                        ny = Interpolations.lerp(ny, mesh.normData[i * 3 + 1], factor);
                        nz = Interpolations.lerp(nz, mesh.normData[i * 3 + 2], factor);
                    }

                    this.temporary.posData[i * 3] = x;
                    this.temporary.posData[i * 3 + 1] = y;
                    this.temporary.posData[i * 3 + 2] = z;
                    this.temporary.texData[i * 2] = u;
                    this.temporary.texData[i * 2 + 1] = v;
                    this.temporary.normData[i * 3] = nx;
                    this.temporary.normData[i * 3 + 1] = ny;
                    this.temporary.normData[i * 3 + 2] = nz;
                }
            }

            return this.temporary;
        }
    }
}
