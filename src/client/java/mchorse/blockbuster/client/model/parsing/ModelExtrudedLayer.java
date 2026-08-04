package mchorse.blockbuster.client.model.parsing;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.client.model.ModelCustom;
import mchorse.blockbuster.client.model.ModelCustomRenderer;
import mchorse.mclib.McLib;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Model extruded layer class (P79 — Fabric 1.20.4 port)
 *
 * <p>Port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer}. It
 * voxelizes a limb's texture region into per-face-bit voxels honoring
 * {@code extrudeMaxFactor}/{@code extrudeInwards}, meshes it with naive
 * per-voxel quads + hidden-face culling, and caches the result per
 * (renderer, texture).</p>
 *
 * <p>The CPU voxelization/meshing math is a verbatim port of the legacy
 * {@code fillChunk}/{@code generateGeometry}/{@code calculateOffset}/{@code Chunk}
 * — the geometry it produces is a load-bearing behavior contract (old skins must
 * extrude to the same shape). The two GL-era pieces are swapped for their modern
 * analogues:</p>
 *
 * <ul>
 *   <li>Legacy stored an OpenGL display-list id per (renderer, texture) and
 *       drew it with {@code glCallList}. Here the cache stores a baked CPU
 *       {@link Mesh} (list of quads) instead. The legacy {@code -1} "failed
 *       generation" sentinel — which stopped a broken skin from being retried
 *       every frame — is preserved as a {@code null} value guarded by
 *       {@code containsKey} (exactly the legacy retry gate). GL-list frees
 *       become mesh-cache drops. Actual GPU residency is deferred to P86.</li>
 *   <li>Legacy read pixels from a {@link BufferedImage} produced by
 *       {@code ImageIO.read(...)}. This port reads through the {@link IImagePixels}
 *       abstraction so either a {@link BufferedImage} (tests, and the default
 *       {@code ImageIO} loader — matching legacy exactly) or a
 *       {@link NativeImage} (the S7 texture pipeline) can back it. Only the
 *       alpha channel (top byte) is ever sampled, so ARGB vs. ABGR packing is
 *       immaterial.</li>
 * </ul>
 *
 * <p><b>Seams.</b> The legacy caches key on {@code ModelCustomRenderer} and the
 * routing lives in {@code ModelCustomRenderer.renderDisplayList()} (P75), which
 * feeds {@code RenderCustomModel.lastTexture} (P77/P80) as the texture whose
 * pixels decide the mesh. Neither type exists in this tree yet, so the renderer
 * key here is an opaque {@link Object} and the render inputs (limb, texture size,
 * extrude factors) are carried by {@link ExtrudeParams}. When P75 lands,
 * {@code ModelCustomRenderer} becomes the key and supplies the params; see the
 * {@code SEAM(P75)} markers. Multiskin deferral ({@code MultiskinThread}) is an
 * S7 seam — until then {@link MultiResourceLocation} images are treated as
 * unavailable and generation is skipped (retried next frame), matching the
 * legacy {@code MultiskinThread.add(...); return;} deferral.</p>
 */
public class ModelExtrudedLayer
{
    public static final byte TOP_BIT = 0b1;
    public static final byte BOTTOM_BIT = 0b10;
    public static final byte FRONT_BIT = 0b100;
    public static final byte BACK_BIT = 0b1000;
    public static final byte LEFT_BIT = 0b10000;
    public static final byte RIGHT_BIT = 0b100000;

    /**
     * Storage for extruded layers. Legacy:
     * {@code Map<ModelCustomRenderer, Map<ResourceLocation, Integer>>}
     * (display-list ids, {@code -1} for a failed generation). Here the value is
     * a baked {@link Mesh}, or {@code null} for a failed generation. A key
     * present with a {@code null} value ({@code containsKey} true) is the
     * legacy {@code -1} retry gate — the failed skin is not regenerated.
     *
     * <p>SEAM(P75): key is {@code ModelCustomRenderer} once it exists.</p>
     */
    protected static Map<Object, Map<ResourceLocation, Mesh>> layers = new HashMap<>();

    /**
     * Cached images.
     */
    protected static Map<ResourceLocation, CachedImage> images = new HashMap<>();

    /**
     * Pluggable image loader. Default replicates the legacy path
     * ({@code ImageIO.read(resourceManager.getResource(texture).getInputStream())}).
     * Tests inject in-memory images through this seam.
     */
    public static IImageProvider imageProvider = ModelExtrudedLayer::defaultImage;

    public static void forceReload(ResourceLocation location, BufferedImage image)
    {
        forceReload(location, IImagePixels.of(image));
    }

    public static void forceReload(ResourceLocation location, IImagePixels image)
    {
        CachedImage cached = new CachedImage(image);

        cached.timer = Integer.MAX_VALUE;
        images.put(location, cached);
    }

    /**
     * Look up (generating on demand) the baked mesh for the given renderer and
     * texture. Returns {@code null} when there is nothing to draw this frame —
     * either the generation failed (cached, not retried) or the image is being
     * deferred (not cached, retried next call). This mirrors the legacy
     * {@code render3DLayer} cache lookup ({@code id == -1} → draw nothing).
     *
     * <p>SEAM(P75): {@code renderer} is a {@code ModelCustomRenderer}.</p>
     */
    public static Mesh getLayer(Object renderer, ResourceLocation texture, ExtrudeParams params)
    {
        Map<ResourceLocation, Mesh> map = layers.get(renderer);

        if (map == null)
        {
            map = new HashMap<>();
            layers.put(renderer, map);
        }

        if (!map.containsKey(texture))
        {
            generateLayer(texture, params, map);
        }

        return map.get(texture);
    }

    /**
     * Render the extruded 3D layer for the given renderer and texture, emitting
     * the cached mesh into {@code consumer}. Generates the layer lazily on the
     * first call for a (renderer, texture) pair.
     *
     * <p>SEAM(P75/P77/P80): the caller is
     * {@code ModelCustomRenderer.renderDisplayList()} with
     * {@code RenderCustomModel.lastTexture} — the currently bound skin, whose
     * pixels decide the mesh.</p>
     */
    public static void render3DLayer(Object renderer, ResourceLocation texture, ExtrudeParams params, VertexConsumer consumer, MatrixStack.Entry entry, int light, int overlay, float r, float g, float b, float a)
    {
        Mesh mesh = getLayer(renderer, texture, params);

        if (mesh != null)
        {
            emit(mesh, consumer, entry, light, overlay, r, g, b, a);
        }
    }

    /**
     * Emit a baked mesh into a vertex consumer using the standard entity vertex
     * format (position, color, uv, overlay, light, normal). No GPU residency —
     * this is a per-frame CPU emit (P86 will bake to a VBO).
     */
    public static void emit(Mesh mesh, VertexConsumer consumer, MatrixStack.Entry entry, int light, int overlay, float r, float g, float b, float a)
    {
        for (Quad quad : mesh.quads)
        {
            for (Vertex v : quad.vertices)
            {
                consumer.vertex(entry.getPositionMatrix(), v.x, v.y, v.z)
                    .color(r, g, b, a)
                    .texture(v.u, v.v)
                    .overlay(overlay)
                    .light(light)
                    .normal(entry.getNormalMatrix(), v.nx, v.ny, v.nz)
                    .next();
            }
        }
    }

    /**
     * Tick the cache to clean up the data. Legacy decremented every image's
     * timer each client tick, flushing and evicting at {@code <= 0}.
     */
    public static void tickCache()
    {
        /* Clean up cache */
        if (!images.isEmpty())
        {
            Iterator<CachedImage> it = images.values().iterator();

            while (it.hasNext())
            {
                CachedImage image = it.next();

                if (image.timer <= 0)
                {
                    image.image.flush();
                    it.remove();
                }

                image.timer -= 1;
            }
        }
    }

    /**
     * Generate extruded layer mesh for given texture location, storing the
     * result (or {@code null} on failure) into {@code map}. A deferred
     * multiskin image returns without touching {@code map} so it is retried.
     */
    private static void generateLayer(ResourceLocation texture, ExtrudeParams params, Map<ResourceLocation, Mesh> map)
    {
        Mesh mesh = null;

        try
        {
            CachedImage image = images.get(texture);

            if (image == null)
            {
                /* Multi-threaded multi-skins freak out when the resource manager
                 * gets called on them. SEAM(S7): MultiskinThread.add(...) will
                 * eventually forceReload the composited image; until then the
                 * image is unavailable, so defer (return without caching → retry
                 * next frame), exactly like the legacy deferral. */
                if (texture instanceof MultiResourceLocation && McLib.multiskinMultiThreaded.get())
                {
                    return;
                }

                image = new CachedImage(imageProvider.load(texture));
                images.put(texture, image);
            }

            if (image.timer > 20)
            {
                image.timer = 20;
            }

            Chunk chunk = fillChunk(image.image, params);

            if (chunk.stats > 0)
            {
                mesh = generateGeometry(chunk, params);
            }
        }
        catch (Exception e)
        {
            System.err.println("An error occurred during construction of extruded 3D layer for texture " + texture + " and limb " + params.limb.name);
            e.printStackTrace();
        }

        map.put(texture, mesh);
    }

    /**
     * Fill chunk based on given texture.
     *
     * <p>Verbatim port of the legacy voxelizer: it fills the chunk with the
     * outer voxels for the six faces of the standard Minecraft cube unwrap,
     * offset by the limb's texture position. Opaque texels (alpha
     * {@code >= 0x80}) set their face bit {@code efi} layers deep inward.</p>
     */
    static Chunk fillChunk(IImagePixels image, ExtrudeParams params)
    {
        final int threshold = 0x80;

        /* Extrude factor */
        int ef = params.extrudeMaxFactor;
        int stepX = (int) (image.getWidth() / params.textureWidth);
        int stepY = (int) (image.getHeight() / params.textureHeight);
        int oStepX = stepX;
        int oStepY = stepY;

        if (ef > 1)
        {
            ef = Math.min(ef, Math.min(stepX, stepY));

            if (stepX > 1) stepX = (int) (image.getWidth() / (params.textureWidth * ef));
            if (stepY > 1) stepY = (int) (image.getHeight() / (params.textureHeight * ef));
        }

        /* Extrude Factor Inwards */
        int efi = MathUtils.clamp(params.extrudeInwards, 1, ef);
        int w = params.limb.size[0];
        int h = params.limb.size[1];
        int d = params.limb.size[2];

        Chunk chunk = new Chunk(w, h, d, ef);

        int offsetX = params.limb.texture[0];
        int offsetY = params.limb.texture[1];

        /* Top & bottom */
        int x = (offsetX + d) * oStepX;
        int y = offsetY * oStepY;

        for (int i = 0; i < chunk.w; i++)
        {
            for (int j = 0; j < chunk.d; j++)
            {
                int alpha = image.getPixel(x + i * stepX, y + j * stepY) >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(i, chunk.h - 1 - k, j, TOP_BIT);
                    }
                }
            }
        }

        x = (offsetX + d + w) * oStepX;
        y = offsetY * oStepY;

        for (int i = 0; i < chunk.w; i++)
        {
            for (int j = 0; j < chunk.h; j++)
            {
                int alpha = image.getPixel(x + i * stepX, y + j * stepY) >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(i, k, j, BOTTOM_BIT);
                    }
                }
            }
        }

        /* Front & back */
        x = (offsetX + d) * oStepX;
        y = (offsetY + d) * oStepY;

        for (int i = 0; i < chunk.w; i++)
        {
            for (int j = 0; j < chunk.h; j++)
            {
                int alpha = image.getPixel(x + i * stepX, y + j * stepY) >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(i, chunk.h - j - 1, chunk.d - 1 - k, FRONT_BIT);
                    }
                }
            }
        }

        x = (offsetX + d * 2 + w) * oStepX;
        y = (offsetY + d) * oStepY;

        for (int i = 0; i < chunk.w; i++)
        {
            for (int j = 0; j < chunk.h; j++)
            {
                int alpha = image.getPixel(x + i * stepX, y + j * stepY) >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(chunk.w - i - 1, chunk.h - j - 1, k, BACK_BIT);
                    }
                }
            }
        }

        /* Left & right */
        x = offsetX * oStepX;
        y = (offsetY + d) * oStepY;

        for (int i = 0; i < chunk.d; i++)
        {
            for (int j = 0; j < chunk.h; j++)
            {
                int alpha = image.getPixel(x + i * stepX, y + j * stepY) >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(k, chunk.h - j - 1, i, LEFT_BIT);
                    }
                }
            }
        }

        x = (offsetX + d + w) * oStepX;
        y = (offsetY + d) * oStepY;

        for (int i = 0; i < chunk.d; i++)
        {
            for (int j = 0; j < chunk.h; j++)
            {
                int xx = x + i * stepX;
                int yy = y + j * stepY;

                int color = image.getPixel(xx, yy);
                int alpha = color >> 24 & 0xff;

                if (alpha >= threshold)
                {
                    for (int k = 0; k < efi; k ++)
                    {
                        chunk.setBlockBit(chunk.w - 1 - k, chunk.h - j - 1, chunk.d - i - 1, RIGHT_BIT);
                    }
                }
            }
        }

        return chunk;
    }

    /**
     * Generate geometry based on given chunk. This method is basically using
     * stupid (instead of greedy) voxel meshing in order to compile the
     * geometry. Verbatim port of the legacy meshing math — mirror flips X in
     * both the voxel lookup and the min/max emission.
     *
     * <p>Legacy TODO preserved as-shipped behavior: UV mapping on non-primary
     * sides is texel-sized and does not match high-res edges. Do not "improve"
     * it.</p>
     */
    static Mesh generateGeometry(Chunk chunk, ExtrudeParams params)
    {
        Mesh mesh = new Mesh();

        int ef = chunk.ef;
        int w = params.limb.size[0] * ef;
        int h = params.limb.size[1] * ef;
        int d = params.limb.size[2] * ef;
        float f = 1F / 16F / ef;
        float so = params.limb.sizeOffset * ef;

        float tw = params.textureWidth * ef;
        float th = params.textureHeight * ef;
        int offsetX = params.limb.texture[0] * ef;
        int offsetY = params.limb.texture[1] * ef;
        boolean mirror = params.limb.mirror;
        Offset off = new Offset(0, 0);
        Offset offmax = new Offset(0, 0);

        for (int x = 0; x < chunk.w; x++)
        {
            for (int y = 0; y < chunk.h; y++)
            {
                for (int z = 0; z < chunk.d; z++)
                {
                    int blockX = mirror ? w - x - 1 : x;
                    byte block = chunk.getBlock(blockX, y, z);

                    if (block == 0)
                    {
                        continue;
                    }

                    float sw = w + so * 2;
                    float sh = h + so * 2;
                    float sd = d + so * 2;

                    float aX = -params.limb.anchor[0] * sw + sw;
                    float aY = -params.limb.anchor[1] * sh + sh;
                    float aZ = -params.limb.anchor[2] * sd + sd;

                    /* Minimum and maximum */
                    float mnx = ((x + (mirror ? 1 : 0)) * (sw / (float) w) - aX) * f;
                    float mmx = ((x + (mirror ? 0 : 1)) * (sw / (float) w) - aX) * f;
                    float mny = -(y * (sh / (float) h) - aY) * f;
                    float mmy = -((y + 1) * (sh / (float) h) - aY) * f;
                    float mnz = -(z * (sd / (float) d) - aZ) * f;
                    float mmz = -((z + 1) * (sd / (float) d) - aZ) * f;

                    /* Top & Bottom */
                    if (!chunk.hasBlock(blockX, y + 1, z))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & TOP_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mnx, mmy, mnz, off.x, off.y, 0, -1, 0,
                            mnx, mmy, mmz, off.x, offmax.y, 0, -1, 0,
                            mmx, mmy, mmz, offmax.x, offmax.y, 0, -1, 0,
                            mmx, mmy, mnz, offmax.x, off.y, 0, -1, 0);
                    }

                    if (!chunk.hasBlock(blockX, y - 1, z))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & BOTTOM_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mnx, mny, mnz, off.x, off.y, 0, 1, 0,
                            mmx, mny, mnz, offmax.x, off.y, 0, 1, 0,
                            mmx, mny, mmz, offmax.x, offmax.y, 0, 1, 0,
                            mnx, mny, mmz, off.x, offmax.y, 0, 1, 0);
                    }

                    /* Front & back */
                    if (!chunk.hasBlock(blockX, y, z + 1))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & FRONT_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mnx, mmy, mmz, off.x, off.y, 0, 0, -1,
                            mnx, mny, mmz, off.x, offmax.y, 0, 0, -1,
                            mmx, mny, mmz, offmax.x, offmax.y, 0, 0, -1,
                            mmx, mmy, mmz, offmax.x, off.y, 0, 0, -1);
                    }

                    if (!chunk.hasBlock(blockX, y, z - 1))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & BACK_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mnx, mmy, mnz, offmax.x, off.y, 0, 0, 1,
                            mmx, mmy, mnz, off.x, off.y, 0, 0, 1,
                            mmx, mny, mnz, off.x, offmax.y, 0, 0, 1,
                            mnx, mny, mnz, offmax.x, offmax.y, 0, 0, 1);
                    }

                    /* Left & Right */
                    if (!chunk.hasBlock(blockX + 1, y, z))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & RIGHT_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mmx, mmy, mnz, offmax.x, off.y, 1, 0, 0,
                            mmx, mmy, mmz, off.x, off.y, 1, 0, 0,
                            mmx, mny, mmz, off.x, offmax.y, 1, 0, 0,
                            mmx, mny, mnz, offmax.x, offmax.y, 1, 0, 0);
                    }

                    if (!chunk.hasBlock(blockX - 1, y, z))
                    {
                        if (!calculateOffset(off, offmax, (byte) (block & LEFT_BIT), offsetX, offsetY, w, h, d, blockX, y, z, tw, th))
                        {
                            calculateOffset(off, offmax, block, offsetX, offsetY, w, h, d, blockX, y, z, tw, th);
                        }

                        mesh.quad(
                            mnx, mmy, mnz, off.x, off.y, -1, 0, 0,
                            mnx, mny, mnz, off.x, offmax.y, -1, 0, 0,
                            mnx, mny, mmz, offmax.x, offmax.y, -1, 0, 0,
                            mnx, mmy, mmz, offmax.x, off.y, -1, 0, 0);
                    }
                }
            }
        }

        return mesh;
    }

    private static boolean calculateOffset(Offset offset, Offset max, byte block, int offsetX, int offsetY, int w, int h, int d, int x, int y, int z, float tw, float th)
    {
        /* Right */
        float offX = -1;
        float offY = -1;

        /* Top */
        if ((block & TOP_BIT) != 0)
        {
            offX = offsetX + d + x;
            offY = offsetY + z;
        }
        /* Bottom */
        else if ((block & BOTTOM_BIT) != 0)
        {
            offX = offsetX + d + w + x;
            offY = offsetY + z;
        }
        /* Front */
        else if ((block & FRONT_BIT) != 0)
        {
            offX = offsetX + d + x;
            offY = offsetY + d + h - y - 1;
        }
        /* Back */
        else if ((block & BACK_BIT) != 0)
        {
            offX = offsetX + d * 2 + w * 2 - x - 1;
            offY = offsetY + d + h - y - 1;
        }
        /* Left */
        else if ((block & LEFT_BIT) != 0)
        {
            offX = offsetX + z;
            offY = offsetY + d + h - y - 1;
        }
        else if ((block & RIGHT_BIT) != 0)
        {
            offX = offsetX + d + w + d - z - 1;
            offY = offsetY + d + h - y - 1;
        }

        if (offX == -1 && offY == -1)
        {
            return false;
        }

        float offMX = (offX + 1) / tw;
        float offMY = (offY + 1) / th;
        offX /= tw;
        offY /= th;

        offset.set(offX, offY);
        max.set(offMX, offMY);

        return true;
    }

    /**
     * Clean everything.
     */
    public static void clear()
    {
        layers.clear();
    }

    /**
     * Clean up layers by texture. Legacy freed the GL list; here the mesh entry
     * is simply dropped so the geometry is regenerated on next demand. Runtime
     * skin swaps (S7 GIFs) route here.
     */
    public static void clearByTexture(ResourceLocation texture)
    {
        for (Map<ResourceLocation, Mesh> map : layers.values())
        {
            map.remove(texture);
        }
    }

    /**
     * Clean up layers by a <em>vanilla</em> texture key.
     *
     * <p>{@code /model clear <path>} iterates the vanilla texture map, whose
     * keys are sanitized {@link net.minecraft.util.Identifier}s, while the
     * layer cache is keyed by verbatim (case-preserving) mclib
     * {@link ResourceLocation}s — so an identifier cannot be compared to a
     * layer key directly (a {@code b.a:Anvil/skins/x.png} layer would survive a
     * clear of {@code b.a:anvil/skins/x.png}). This overload projects each
     * layer key through {@link ResourceLocation#toIdentifier()} and drops the
     * ones that collapse onto {@code id}, which is what legacy's identity
     * compare effectively did when both sides were the same object.</p>
     */
    public static void clearByIdentifier(Identifier id)
    {
        if (id == null)
        {
            return;
        }

        for (Map<ResourceLocation, Mesh> map : layers.values())
        {
            Iterator<Map.Entry<ResourceLocation, Mesh>> it = map.entrySet().iterator();

            while (it.hasNext())
            {
                ResourceLocation key = it.next().getKey();

                if (key != null && id.equals(key.toIdentifier()))
                {
                    it.remove();
                }
            }
        }
    }

    /**
     * Clean up layers for a single renderer.
     */
    public static void clearByRenderer(Object renderer)
    {
        layers.remove(renderer);
    }

    /**
     * Clean up layers by model — legacy {@code clearByModel(ModelCustom)}, the
     * loop that {@link #clearByRenderer} was the seam for. Called whenever a
     * model is recompiled (the editor's {@code rebuildModel}, a pack reload) or
     * removed, so the extruded geometry of its {@code is3D} limbs is dropped and
     * regenerated against the new limb definitions.
     *
     * <p>Legacy additionally ran {@code glDeleteLists} on each cached display
     * list; this port caches {@code Mesh} objects instead of display lists (core
     * profile), so dropping the map is the whole cleanup. Null-tolerant, like
     * legacy.</p>
     */
    public static void clearByModel(ModelCustom model)
    {
        if (model == null || model.limbs == null)
        {
            return;
        }

        for (ModelCustomRenderer renderer : model.limbs)
        {
            if (renderer == null || renderer.limb == null || !renderer.limb.is3D)
            {
                continue;
            }

            clearByRenderer(renderer);
        }
    }

    private static IImagePixels defaultImage(ResourceLocation texture) throws Exception
    {
        /* SEAM(S7): the legacy path was
         * ImageIO.read(Minecraft.getMinecraft().getResourceManager()
         *     .getResource(texture).getInputStream()). */
        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
        Optional<Resource> resource = manager.getResource(texture.toIdentifier());

        if (!resource.isPresent())
        {
            throw new FileNotFoundException(texture.toString());
        }

        try (InputStream stream = resource.get().getInputStream())
        {
            return IImagePixels.of(ImageIO.read(stream));
        }
    }

    /**
     * Pluggable image loader seam — see {@link #imageProvider}.
     */
    public interface IImageProvider
    {
        IImagePixels load(ResourceLocation texture) throws Exception;
    }

    /**
     * Abstraction over a source of pixels. Only the alpha byte (top 8 bits of
     * the packed pixel) is ever read by the voxelizer, so ARGB
     * ({@link BufferedImage}) and ABGR ({@link NativeImage}) backings behave
     * identically.
     */
    public interface IImagePixels
    {
        int getWidth();

        int getHeight();

        /**
         * Packed pixel with alpha in the top byte. Bounds errors propagate as
         * exceptions (legacy {@code getRGB} behavior), which the generator
         * catches and turns into a failed-generation sentinel.
         */
        int getPixel(int x, int y);

        default void flush()
        {}

        static IImagePixels of(BufferedImage image)
        {
            return new IImagePixels()
            {
                @Override
                public int getWidth()
                {
                    return image.getWidth();
                }

                @Override
                public int getHeight()
                {
                    return image.getHeight();
                }

                @Override
                public int getPixel(int x, int y)
                {
                    return image.getRGB(x, y);
                }

                @Override
                public void flush()
                {
                    image.flush();
                }
            };
        }

        static IImagePixels of(NativeImage image)
        {
            return new IImagePixels()
            {
                @Override
                public int getWidth()
                {
                    return image.getWidth();
                }

                @Override
                public int getHeight()
                {
                    return image.getHeight();
                }

                @Override
                public int getPixel(int x, int y)
                {
                    /* NativeImage.getColor is ABGR-packed; alpha is still the
                     * top byte, which is all the voxelizer reads. */
                    return image.getColor(x, y);
                }

                @Override
                public void flush()
                {
                    image.close();
                }
            };
        }
    }

    /**
     * The render inputs the voxelizer/mesher needs, decomposed from the legacy
     * {@code ModelCustomRenderer} (SEAM(P75)). {@code extrudeMaxFactor} and
     * {@code extrudeInwards} come from the {@link Model}; {@code textureWidth}/
     * {@code textureHeight} are the renderer's texture size (legacy sets them
     * from {@code model.texture[0]/[1]}).
     */
    public static class ExtrudeParams
    {
        public final ModelLimb limb;
        public final int extrudeMaxFactor;
        public final int extrudeInwards;
        public final float textureWidth;
        public final float textureHeight;

        public ExtrudeParams(ModelLimb limb, int extrudeMaxFactor, int extrudeInwards, float textureWidth, float textureHeight)
        {
            this.limb = limb;
            this.extrudeMaxFactor = extrudeMaxFactor;
            this.extrudeInwards = extrudeInwards;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
        }

        /**
         * Derive params from a limb and its owning model exactly as the legacy
         * {@code ModelCustomRenderer} did: extrude factors from the model,
         * texture size from {@code model.texture}.
         */
        public ExtrudeParams(ModelLimb limb, Model model)
        {
            this(limb, model.extrudeMaxFactor, model.extrudeInwards, model.texture[0], model.texture[1]);
        }
    }

    /**
     * Baked CPU mesh — the modern analogue of the legacy GL display list.
     */
    public static class Mesh
    {
        public final List<Quad> quads = new ArrayList<>();

        void quad(
            float x0, float y0, float z0, float u0, float v0, float n0x, float n0y, float n0z,
            float x1, float y1, float z1, float u1, float v1, float n1x, float n1y, float n1z,
            float x2, float y2, float z2, float u2, float v2, float n2x, float n2y, float n2z,
            float x3, float y3, float z3, float u3, float v3, float n3x, float n3y, float n3z)
        {
            Quad quad = new Quad(
                new Vertex(x0, y0, z0, u0, v0, n0x, n0y, n0z),
                new Vertex(x1, y1, z1, u1, v1, n1x, n1y, n1z),
                new Vertex(x2, y2, z2, u2, v2, n2x, n2y, n2z),
                new Vertex(x3, y3, z3, u3, v3, n3x, n3y, n3z));

            this.quads.add(quad);
        }

        /**
         * Deterministic textual dump used for geometry goldens.
         */
        public String toGolden()
        {
            StringBuilder builder = new StringBuilder();

            builder.append("quads=").append(this.quads.size()).append('\n');

            for (Quad quad : this.quads)
            {
                for (Vertex v : quad.vertices)
                {
                    builder.append(v.toGolden()).append('\n');
                }
            }

            return builder.toString();
        }
    }

    public static class Quad
    {
        public final Vertex[] vertices;

        public Quad(Vertex a, Vertex b, Vertex c, Vertex d)
        {
            this.vertices = new Vertex[] {a, b, c, d};
        }
    }

    public static class Vertex
    {
        public final float x;
        public final float y;
        public final float z;
        public final float u;
        public final float v;
        public final float nx;
        public final float ny;
        public final float nz;

        public Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public String toGolden()
        {
            return String.format(Locale.ROOT,
                "p=%.6f,%.6f,%.6f uv=%.6f,%.6f n=%.0f,%.0f,%.0f",
                this.x, this.y, this.z, this.u, this.v, this.nx, this.ny, this.nz);
        }
    }

    /**
     * Cached image class.
     */
    public static class CachedImage
    {
        public IImagePixels image;
        public int timer = 10;

        public CachedImage(IImagePixels image)
        {
            this.image = image;
        }
    }

    /**
     * Chunk class — a voxel chunk. Verbatim port; byte voxel array with a
     * face-bit per cell, an occupancy counter ({@link #stats}), and
     * bounds-checked accessors.
     */
    public static class Chunk
    {
        /**
         * Array of block data.
         */
        protected byte[] data;

        public final int w;
        public final int h;
        public final int d;

        public int stats;
        public int ef;

        /**
         * Initialize empty chunk data.
         */
        public Chunk(int w, int h, int d, int ef)
        {
            w *= ef;
            h *= ef;
            d *= ef;

            this.w = w;
            this.h = h;
            this.d = d;
            this.ef = ef;

            this.data = new byte[w * h * d];
        }

        /**
         * Set block at given coordinates.
         */
        public void setBlock(int x, int y, int z, byte block)
        {
            if (x < 0 || y < 0 || z < 0 || x >= this.w || y >= this.h || z >= this.d)
            {
                return;
            }

            byte old = this.data[x + y * this.w + z * this.w * this.h];

            this.data[x + y * this.w + z * this.w * this.h] = block;

            if (block != old)
            {
                this.stats += (block == 0) ? -1 : 1;
            }
        }

        /**
         * Set block bit at given coordinates.
         */
        public void setBlockBit(int x, int y, int z, byte bit)
        {
            if (x < 0 || y < 0 || z < 0 || x >= this.w || y >= this.h || z >= this.d)
            {
                return;
            }

            byte old = this.data[x + y * this.w + z * this.w * this.h];
            byte block = (byte) (old | bit);

            this.data[x + y * this.w + z * this.w * this.h] = block;

            if (block != old)
            {
                this.stats += (block == 0) ? -1 : 1;
            }
        }

        /**
         * Is this chunk has a block at given coordinates.
         */
        public boolean hasBlock(int x, int y, int z)
        {
            return this.getBlock(x, y, z) != 0;
        }

        /**
         * Get block at given coordinate.
         */
        public byte getBlock(int x, int y, int z)
        {
            if (x < 0 || y < 0 || z < 0 || x >= this.w || y >= this.h || z >= this.d)
            {
                return 0;
            }

            return this.data[x + y * this.w + z * this.w * this.h];
        }
    }

    public static class Offset
    {
        public float x;
        public float y;

        public Offset(float x, float y)
        {
            this.set(x, y);
        }

        public void set(float x, float y)
        {
            this.x = x;
            this.y = y;
        }
    }
}
