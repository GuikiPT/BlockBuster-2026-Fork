package mchorse.vanilla_pack.render;

import mchorse.mclib.utils.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a flat texture into a 3D "papercraft" mesh: the two full-sprite faces
 * plus a side wall wherever an opaque pixel borders a transparent one (roadmap
 * P54).
 *
 * <p>Port of Metamorph 1.4's
 * {@code mchorse.vanilla_pack.render.ItemExtruder}. Two morphs draw through it
 * and both were dark until this landed: {@code ItemMorph}'s {@code Texture}
 * field (which replaces the item model outright) and {@code ImageMorph}'s
 * {@code thickness} toggle. It is <b>not</b> the same extruder as P79's
 * {@code ModelExtrudedLayer} — that one voxelizes a model limb's texture region
 * with configurable depth, this one emits a fixed {@code 1/16} slab from a whole
 * texture.</p>
 *
 * <p><b>The mesh arithmetic is verbatim</b>, quirks included:</p>
 *
 * <ul>
 *   <li>A pixel counts as solid only when it is <b>fully</b> opaque. Legacy
 *       decoded it into an mclib {@link Color} and tested {@code a >= 1}, i.e.
 *       alpha exactly 255 — a 254-alpha pixel gets no walls and leaves a hole in
 *       the silhouette.</li>
 *   <li>The two full-sprite faces are emitted <b>before</b> any wall and always
 *       span the whole texture, transparent pixels included; it is the alpha
 *       blend, not the geometry, that hides them.</li>
 *   <li>The wall tests are {@code !hasPixel(neighbour) || i == 15} (and
 *       {@code j == 15}). Those two {@code 15}s are hard-coded where everything
 *       around them uses {@code w}/{@code h}, so a texture wider than 16 grows a
 *       spurious internal wall down its 16th column — and one taller than 16
 *       grows one across its 16th row. Preserved: every 2.7.2 extrusion of a
 *       non-16px texture was authored against it.</li>
 *   <li>A wall's four corners all sample the <b>centre</b> of the pixel that
 *       spawned it ({@code (x + 0.5) / w}, {@code (y + 0.5) / h}), so it draws
 *       flat in that pixel's colour.</li>
 * </ul>
 *
 * <p><b>The cache is legacy's, including its bug.</b> A failed read stores
 * {@code null} under the texture's key and then returns {@code null} — but the
 * lookup that opens the method is a plain {@code get}, so a {@code null} value is
 * indistinguishable from an absent key and the read is retried on the very next
 * frame. The negative cache never suppresses anything. That is reproduced rather
 * than repaired, because "the extrusion appears as soon as the resource pack
 * supplies the texture" is behaviour someone may be relying on; the cost is one
 * failed resource lookup per frame for a morph pointing at a missing file. Like
 * legacy, the cache is not dropped on a resource reload either — see
 * {@link #clearCache()}.</p>
 *
 * <p>Reading stays on {@link ImageIO} rather than moving to
 * {@code NativeImage.read}, which matters: legacy's {@code ImageIO.read} decodes
 * a GIF's first frame, where stb (and therefore {@code NativeImage}) refuses the
 * file outright. An image morph pointed at a GIF still extrudes its first
 * frame.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/render/ItemExtruder.java
 */
public class ItemExtruder
{
    /** Half the slab's thickness: legacy {@code 0.5F / 16F}. */
    public static final float DEPTH = 0.5F / 16F;

    private static Map<Identifier, CachedExtrusion> cache = new HashMap<Identifier, CachedExtrusion>();

    /**
     * Pluggable image loader — the same seam shape P79's
     * {@code ModelExtrudedLayer.imageProvider} uses, so the mesh generator can
     * be driven headlessly. The default is legacy's exact path.
     */
    public static IImageProvider imageProvider = ItemExtruder::defaultImage;

    public static CachedExtrusion extrude(Identifier texture)
    {
        CachedExtrusion extrusion = cache.get(texture);

        if (extrusion != null)
        {
            return extrusion;
        }

        BufferedImage pixels = null;

        try
        {
            pixels = imageProvider.load(texture);
        }
        catch (Exception e)
        {
            /* Legacy's ineffective negative cache — see the class note. */
            cache.put(texture, null);

            return null;
        }

        if (pixels == null)
        {
            /* ImageIO answers null (rather than throwing) for a format it has
             * no reader for, which legacy walked straight into as an NPE out of
             * getWidth(). Treated as a failed read instead — same outcome as the
             * catch above, which is "draw nothing". */
            cache.put(texture, null);

            return null;
        }

        extrusion = build(texture, pixels);

        cache.put(texture, extrusion);

        return extrusion;
    }

    /**
     * The mesh generator, split out of {@link #extrude} so it can be run against
     * an in-memory image. Legacy had this inline; nothing about the arithmetic
     * differs.
     */
    public static CachedExtrusion build(Identifier texture, BufferedImage pixels)
    {
        int w = pixels.getWidth();
        int h = pixels.getHeight();
        CachedExtrusion extrusion = new CachedExtrusion(texture, w, h);

        int uv_x = 0;
        int uv_y = 0;

        float p = 0.5F;
        float n = -0.5F;
        float u1 = uv_x / (float) w;
        float v1 = uv_y / (float) h;
        float u2 = (uv_x + w) / (float) w;
        float v2 = (uv_y + h) / (float) h;
        float d = DEPTH;

        fillTexturedNormalQuad(extrusion,
            p, n, d,
            n, n, d,
            n, p, d,
            p, p, d,
            u1, v1, u2, v2,
            0F, 0F, 1F
        );

        fillTexturedNormalQuad(extrusion,
            n, n, -d,
            p, n, -d,
            p, p, -d,
            n, p, -d,
            u2, v1, u1, v2,
            0F, 0F, -1F
        );

        for (int i = 0; i < w; i++)
        {
            for (int j = 0; j < h; j++)
            {
                int x = uv_x + i;
                int y = uv_y + j;

                if (hasPixel(pixels, x, y))
                {
                    generateNeighbors(pixels, extrusion, i, j, x, y, d, w, h);
                }
            }
        }

        extrusion.flush();

        return extrusion;
    }

    private static void generateNeighbors(BufferedImage pixels, CachedExtrusion extrusion, int i, int j, int x, int y, float d, float w, float h)
    {
        float u = (x + 0.5F) / w;
        float v = (y + 0.5F) / h;

        if (!hasPixel(pixels, x - 1, y) || i == 0)
        {
            fillTexturedNormalQuad(extrusion,
                i / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -j / h + 0.5F, -d,
                i / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, d,
                u, v, u, v,
                -1F, 0F, 0F
            );
        }

        if (!hasPixel(pixels, x + 1, y) || i == 15)
        {
            fillTexturedNormalQuad(extrusion,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, -d,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                u, v, u, v,
                1F, 0F, 0F
            );
        }

        if (!hasPixel(pixels, x, y - 1) || j == 0)
        {
            fillTexturedNormalQuad(extrusion,
                (i + 1) / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -j / h + 0.5F, d,
                i / w - 0.5F, -j / h + 0.5F, -d,
                (i + 1) / w - 0.5F, -j / h + 0.5F, -d,
                u, v, u, v,
                0F, 1F, 0F
            );
        }

        if (!hasPixel(pixels, x, y + 1) || j == 15)
        {
            fillTexturedNormalQuad(extrusion,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, -d,
                i / w - 0.5F, -(j + 1) / h + 0.5F, d,
                (i + 1) / w - 0.5F, -(j + 1) / h + 0.5F, d,
                u, v, u, v,
                0F, -1F, 0F
            );
        }
    }

    /**
     * Legacy: out of bounds is transparent, and only a fully opaque pixel counts
     * (mclib's {@link Color} decodes ARGB into 0–1 floats, and legacy tested
     * {@code a >= 1}).
     */
    public static boolean hasPixel(BufferedImage pixels, int x, int y)
    {
        if (x < 0 || x >= pixels.getWidth() || y < 0 || y >= pixels.getHeight())
        {
            return false;
        }

        Color pixel = new Color().set(pixels.getRGB(x, y));

        return pixel != null && pixel.a >= 1;
    }

    /**
     * Fill a quad for vertex-normal-uv-rgba. Points should
     * be supplied in this order:
     *
     * <pre>
     *     3 -------&gt; 4
     *     ^
     *     |
     *     |
     *     2 &lt;------- 1
     * </pre>
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and
     * right is +X, in case of a quad on fixed on Z axis.
     *
     * <p>Legacy wrote two triangles here — {@code (2,1,4)} then {@code (2,4,3)}
     * — because it drew with {@code GL_TRIANGLES}. The four corners
     * {@code 2, 1, 4, 3} are the quad those tessellate; see
     * {@link CachedExtrusion}.</p>
     */
    public static void fillTexturedNormalQuad(CachedExtrusion extrusion, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float nx, float ny, float nz)
    {
        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        extrusion.addVertex(x2, y2, z2, nx, ny, nz, u1, v2);
        extrusion.addVertex(x1, y1, z1, nx, ny, nz, u2, v2);
        extrusion.addVertex(x4, y4, z4, nx, ny, nz, u2, v1);
        extrusion.addVertex(x3, y3, z3, nx, ny, nz, u1, v1);
    }

    /**
     * Legacy never invalidated its cache — a resource-pack reload kept the old
     * mesh for the rest of the session. Exposed for the tests, and as the hook a
     * reload listener would call if that is ever judged worth deviating on.
     */
    public static void clearCache()
    {
        cache.clear();
    }

    private static BufferedImage defaultImage(Identifier texture) throws Exception
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ResourceManager manager = mc == null ? null : mc.getResourceManager();

        if (manager == null)
        {
            throw new IllegalStateException("No resource manager for " + texture);
        }

        Optional<Resource> resource = manager.getResource(texture);

        if (!resource.isPresent())
        {
            throw new FileNotFoundException(texture.toString());
        }

        try (InputStream stream = resource.get().getInputStream())
        {
            return ImageIO.read(stream);
        }
    }

    /** Pluggable image loader seam — see {@link #imageProvider}. */
    public interface IImageProvider
    {
        BufferedImage load(Identifier texture) throws Exception;
    }
}
