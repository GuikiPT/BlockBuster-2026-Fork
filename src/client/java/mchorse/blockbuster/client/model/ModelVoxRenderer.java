package mchorse.blockbuster.client.model;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.api.formats.Mesh;
import mchorse.blockbuster.api.formats.vox.MeshesVOX;
import mchorse.blockbuster.api.formats.vox.data.VoxTexture;
import mchorse.blockbuster.client.compat.iris.IrisPbrGifBridge;
import mchorse.blockbuster.client.textures.MaterialTextures;
import mchorse.blockbuster.client.textures.TextureRegistry;
import mchorse.blockbuster.client.textures.VoxPaletteTexture;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * VOX limb renderer (roadmap P78).
 *
 * <p>Port of Blockbuster 2.7.2's {@code client/model/ModelVoxRenderer}.
 * MagicaVoxel limbs render at 1/16 scale with an origin offset and a
 * palette-strip texture. The vertex transform is separated from the GL upload
 * so it is headless-testable ({@link #bakeVertices()}): every mesh vertex maps
 * to {@code x = (pos - origin)/16, y = -(pos - origin)/16, z = (pos -
 * origin)/16} with the Y normal negated — mirroring the OBJ path.</p>
 *
 * <p>{@code mesh.triangles} is a <b>vertex</b> count, not a triangle count —
 * every loop iterates it over vertices.</p>
 *
 * <p><b>Palette identifier (S7/P91.1).</b> Legacy re-registered every limb's
 * palette under the one shared {@link #VOXTEX} identifier immediately before
 * binding it and drawing — last-writer-wins was harmless because the draw was
 * immediate-mode. On 1.20.4 geometry is batched per {@code RenderLayer} and
 * flushed later, so two vox limbs sharing one identifier would both rasterise
 * with whichever palette was registered last. Each renderer therefore gets its
 * own identifier derived from {@link #VOXTEX}; the shared constant stays as the
 * legacy key (and the prefix every generated one carries).</p>
 */
public class ModelVoxRenderer extends ModelCustomRenderer
{
    /** The legacy shared palette identifier — kept as the naming root. */
    public static final ResourceLocation VOXTEX = new ResourceLocation("blockbuster", "textures/dynamic_vox");

    private static final AtomicInteger PALETTE_SERIAL = new AtomicInteger();

    public MeshesVOX mesh;
    public VoxTexture texture;

    /**
     * Where {@link #texture}'s pixels are registered, or {@code null} when this
     * limb has not baked yet (or baked without a client).
     */
    public Identifier paletteTex;

    /** Transformed triangle-soup vertices [x,y,z,u,v,nx,ny,nz]. */
    protected float[][] verts;

    public ModelVoxRenderer(ModelCustom model, ModelLimb limb, ModelTransform transform, MeshesVOX mesh)
    {
        super(model, limb, transform);

        this.mesh = mesh;
        this.min = mesh.getMin();
        this.max = mesh.getMax();
    }

    /**
     * Build the transformed vertex soup (GL-free). Mirrors legacy
     * {@code compileDisplayList} vertex math; also builds the palette texture
     * and drops the source mesh (memory), matching legacy.
     */
    public float[][] bakeVertices()
    {
        Mesh mesh = this.mesh.build();

        float[][] out = new float[mesh.triangles][];

        for (int i = 0, c = mesh.triangles; i < c; i++)
        {
            float x = (mesh.posData[i * 3] - this.limb.origin[0]) / 16F;
            float y = -(mesh.posData[i * 3 + 1] - this.limb.origin[1]) / 16F;
            float z = (mesh.posData[i * 3 + 2] - this.limb.origin[2]) / 16F;

            float u = mesh.texData[i * 2];
            float v = mesh.texData[i * 2 + 1];

            float nx = mesh.normData[i * 3];
            float ny = -mesh.normData[i * 3 + 1];
            float nz = mesh.normData[i * 3 + 2];

            out[i] = new float[] {x, y, z, u, v, nx, ny, nz};
        }

        return out;
    }

    @Override
    public void bake()
    {
        this.verts = this.bakeVertices();
        this.texture = new VoxTexture(this.mesh.document.palette, this.limb.specular);
        this.mesh = null;
        this.compiled = true;

        this.uploadPalette();
    }

    /**
     * Register the palette as a {@code width×rows} texture (legacy
     * {@code TextureManager.loadTexture(VOXTEX, this.texture)} — a
     * {@code DynamicTexture} of ARGB ints, here a {@code NativeImage} with the
     * channel swap). No-op without a client, so headless bakes stay GL-free.
     *
     * <p><b>Shader packs (S21/P217.3).</b> The uploaded object is a
     * {@link VoxPaletteTexture} rather than a bare
     * {@code NativeImageBackedTexture} purely so Iris can recognise it: its PBR
     * loader registry keys on the texture's class. The pixels, the dimensions
     * and the identifier are exactly what P91.1 landed — the palette keeps
     * {@link VoxTexture#ROWS_LEGACY}, and the normal/specular maps legacy packed
     * into Optifine's tripled buffer are served separately by
     * {@link IrisPbrGifBridge}.</p>
     */
    protected void uploadPalette()
    {
        if (this.texture == null || MinecraftClient.getInstance() == null)
        {
            return;
        }

        try
        {
            Identifier id = this.paletteTex != null
                ? this.paletteTex
                : new Identifier(VOXTEX.getResourceDomain(), VOXTEX.getResourcePath() + "_" + PALETTE_SERIAL.getAndIncrement());

            VoxPaletteTexture palette = new VoxPaletteTexture(this.texture);

            TextureRegistry.get().register(id, palette);

            this.paletteTex = id;

            /* Inert without Iris — see IrisPbrGifBridge.trackPalette. */
            IrisPbrGifBridge.trackPalette(palette);
        }
        catch (Throwable t)
        {
            /* Total: a palette that will not upload draws with the model's own
             * skin rather than crashing the limb. */
            Blockbuster.LOGGER.warn("Failed to upload a VOX palette texture", t);
        }
    }

    @Override
    protected void emit(MatrixStack matrices, VertexConsumer consumer, float scale, float r, float g, float b, float a, int light, int overlay)
    {
        this.emit(matrices, consumer, MaterialTextures.ambient(), r, g, b, a, light, overlay);
    }

    /**
     * Emit the limb into the buffer its palette belongs to (S7/P91.1). Legacy
     * bound the palette, drew, then restored the entity skin via
     * {@code RenderCustomModel.bindLastTexture()}; picking the palette's own
     * consumer is that pair, and a limb whose palette never uploaded falls back
     * to the caller's consumer.
     */
    public void emit(MatrixStack matrices, VertexConsumer consumer, MaterialTextures.Consumers consumers, float r, float g, float b, float a, int light, int overlay)
    {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pose = entry.getPositionMatrix();

        consumer = MaterialTextures.pick(consumers, this.paletteTex, consumer);

        for (int i = 0; i < this.verts.length; i += 3)
        {
            /* Emit each triangle as a degenerate quad (repeat the last vertex)
             * so it fits the entity quad vertex format. */
            emitVertex(consumer, entry, pose, this.verts[i], r, g, b, a, light, overlay);
            emitVertex(consumer, entry, pose, this.verts[i + 1], r, g, b, a, light, overlay);
            emitVertex(consumer, entry, pose, this.verts[i + 2], r, g, b, a, light, overlay);
            emitVertex(consumer, entry, pose, this.verts[i + 2], r, g, b, a, light, overlay);
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

        this.texture = null;

        /* Legacy: this.texture.deleteGlTexture(). */
        if (this.paletteTex != null)
        {
            try
            {
                if (MinecraftClient.getInstance() != null)
                {
                    TextureRegistry.get().delete(this.paletteTex);
                }
            }
            catch (Throwable t)
            {
                /* Total: tearing a limb down never throws. */
            }

            this.paletteTex = null;
        }
    }
}
