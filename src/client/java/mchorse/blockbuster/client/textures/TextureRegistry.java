package mchorse.blockbuster.client.textures;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.commands.model.ITextureRegistryOps;
import mchorse.blockbuster.utils.TextureUtils;
import mchorse.mclib.utils.ReflectionUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * S7/P87 — first-class runtime texture registry, the API the S12/P140 texture
 * manager panel drives.
 *
 * <p>Legacy Blockbuster (via {@code GuiTextureManagerPanel}) mutated the vanilla
 * texture map directly through {@code ReflectionUtils.getTextures}: register,
 * re-register (delete GL + reload), alias/replace, remove, filter/mipmap toggle,
 * export-to-PNG. The port keeps that "operate on the real map, no shadow
 * registry that can drift" contract (a quirk called out in the plan), but
 * backs it with the {@link TextureManagerAccessor}-backed
 * {@link ReflectionUtils#getTextures} instead of reflection, and closes replaced
 * {@link AbstractTexture}s (1.20.4 native-memory: a leaked/double-closed
 * NativeImage is a hard crash, unlike silent 1.12.2 GL).</p>
 *
 * <p>The GL-touching operations (filter application, export readback) are thin
 * and exercised only by source-level parity review; all decision logic (the
 * filter enum matrix, the RGBA&rarr;ARGB export repack, the close-on-replace
 * bookkeeping) is pulled out as pure functions / map surgery the headless tests
 * cover.</p>
 */
public class TextureRegistry
{
    /**
     * Legacy {@code SubCommandModelClear}'s hardcoded dynamic-texture domain
     * list, verbatim: {@code c.s} (Chameleon/custom skins), {@code s&b}
     * (Skin&amp;Bones), {@code b.a} (Blockbuster actors), {@code http} and
     * {@code https}. Kept in the legacy spelling for diff-ability — matching
     * happens through {@link #DYNAMIC_NAMESPACES}.
     */
    public static final List<String> DYNAMIC_DOMAINS = Collections.unmodifiableList(
        Arrays.asList("c.s", "s&b", "b.a", "http", "https"));

    /**
     * {@link #DYNAMIC_DOMAINS} projected into the alphabet vanilla's texture map
     * actually uses. 1.12.2 keys were raw {@code ResourceLocation}s, so the
     * legacy strings could be compared directly; 1.20.4 {@link Identifier}
     * namespaces are validated, so a texture registered from the {@code s&b}
     * domain lives under {@code s_b} (see
     * {@link ResourceLocation#sanitizeDomain}). Resolves S5 open question 8:
     * the shim needs no escaping scheme — the sanitizer <em>is</em> the scheme,
     * and {@code /model clear} matches on its output.
     */
    public static final Set<String> DYNAMIC_NAMESPACES;

    static
    {
        Set<String> namespaces = new LinkedHashSet<String>();

        for (String domain : DYNAMIC_DOMAINS)
        {
            namespaces.add(ResourceLocation.sanitizeDomain(domain));
        }

        DYNAMIC_NAMESPACES = Collections.unmodifiableSet(namespaces);
    }

    private final Map<Identifier, AbstractTexture> textures;

    public TextureRegistry(Map<Identifier, AbstractTexture> textures)
    {
        this.textures = textures;
    }

    /**
     * Production registry over the live {@code TextureManager.textures} map.
     */
    public static TextureRegistry get()
    {
        return new TextureRegistry(ReflectionUtils.getTextures(MinecraftClient.getInstance().getTextureManager()));
    }

    public Map<Identifier, AbstractTexture> map()
    {
        return this.textures;
    }

    /**
     * Legacy {@code GuiTextureManagerPanel.open()} — a sorted snapshot of the
     * currently-registered identifiers for the searchable list.
     */
    public List<Identifier> snapshot()
    {
        List<Identifier> list = new ArrayList<Identifier>(this.textures.keySet());

        Collections.sort(list, (a, b) -> a.toString().compareTo(b.toString()));

        return list;
    }

    public AbstractTexture get(Identifier id)
    {
        return this.textures.get(id);
    }

    /**
     * The shared missing-texture sentinel — 1.20.4's stand-in for 1.12.2's
     * {@code TextureUtil.MISSING_TEXTURE}. {@code TextureManager.loadTexture}
     * stores {@link MissingSprite#getMissingSpriteTexture()} into the map under
     * the requested identifier whenever a texture fails to load, and vanilla
     * itself never closes it — the exact shape of the legacy
     * {@code "loaded but missing"} state and of the legacy
     * {@code texture != MISSING_TEXTURE} delete guard.
     *
     * <p>Total: the sentinel is lazily built from a {@link NativeImage}, so the
     * accessor answers {@code null} instead of throwing when LWJGL natives are
     * unavailable (headless). A {@code null} sentinel simply means "nothing can
     * be classified as MISSING", never a crash.</p>
     */
    public static AbstractTexture missingTexture()
    {
        if (missingUnavailable)
        {
            return null;
        }

        try
        {
            return MissingSprite.getMissingSpriteTexture();
        }
        catch (Throwable t)
        {
            /* Latch the headless answer: vanilla memoizes only on success, so
             * without this every close-on-replace would re-raise the native
             * link error. */
            missingUnavailable = true;

            return null;
        }
    }

    /** Set once the missing-sprite sentinel proves unbuildable (headless). */
    private static boolean missingUnavailable;

    /**
     * Pure classification behind {@code /model report}'s per-image suffix, the
     * 1:1 shape of legacy's
     * {@code texture == MISSING_TEXTURE ? ", loaded but missing" : texture != null ? ", loaded" : ""}.
     */
    public static ITextureRegistryOps.Status classify(AbstractTexture texture, AbstractTexture missing)
    {
        if (texture == null)
        {
            return ITextureRegistryOps.Status.ABSENT;
        }

        return texture == missing ? ITextureRegistryOps.Status.MISSING : ITextureRegistryOps.Status.LOADED;
    }

    /**
     * {@code /model report}'s status query.
     *
     * <p>Reads the backing map <em>directly</em> rather than through
     * {@code TextureManager.getTexture}: on 1.20.4 that method lazily
     * constructs and registers a {@code ResourceTexture} for any unknown
     * identifier, which would (a) make every reported image come back
     * {@code LOADED}/{@code MISSING} and never {@code ABSENT} and (b) leave a
     * report command mutating the texture map. 1.12.2's {@code getTexture} was
     * a plain map lookup, so the map lookup is the faithful port.</p>
     */
    public ITextureRegistryOps.Status status(Identifier id, AbstractTexture missing)
    {
        return classify(this.textures.get(id), missing);
    }

    /**
     * {@code /model clear [path]}'s eviction — legacy
     * {@code SubCommandModelClear}'s loop over the vanilla texture map.
     *
     * <p>Every entry whose namespace is one of {@link #DYNAMIC_NAMESPACES} and
     * whose path starts with {@code prefix} is dropped from the map and its
     * GL/native resources released ({@link AbstractTexture#close()}, the 1.20.4
     * replacement for {@code deleteGlTexture()}), except the shared
     * {@code missing} sentinel which is evicted but never closed — legacy's
     * {@code texture != TextureUtil.MISSING_TEXTURE} guard, and the same guard
     * vanilla's own {@code registerTexture} uses.</p>
     *
     * <p>An empty prefix matches every path ({@code String.startsWith("")}), so
     * {@code /model clear} with no argument evicts all dynamic textures — legacy
     * behaviour, kept including the fact that non-Blockbuster domains are never
     * touched.</p>
     *
     * @return the evicted keys, in map iteration order, so the caller can drop
     *         the matching extruded layers (legacy did it inline).
     */
    public List<Identifier> clearDynamic(String prefix, AbstractTexture missing)
    {
        String path = ResourceLocation.sanitizePath(prefix == null ? "" : prefix);
        List<Identifier> removed = new ArrayList<Identifier>();
        List<AbstractTexture> close = new ArrayList<AbstractTexture>();
        Iterator<Map.Entry<Identifier, AbstractTexture>> it = this.textures.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Identifier, AbstractTexture> entry = it.next();
            Identifier key = entry.getKey();
            AbstractTexture texture = entry.getValue();

            if (texture == null || !DYNAMIC_NAMESPACES.contains(key.getNamespace()) || !key.getPath().startsWith(path))
            {
                continue;
            }

            removed.add(key);
            close.add(texture);
            it.remove();
        }

        for (AbstractTexture texture : close)
        {
            if (texture != missing)
            {
                try
                {
                    texture.close();
                }
                catch (Exception e)
                {
                    /* Total: a texture that refuses to release is logged-and-
                     * ignored, never a crash mid-command. */
                    Blockbuster.LOGGER.warn("Failed to release a cleared texture", e);
                }
            }
        }

        return removed;
    }

    /**
     * Register (or re-register) a texture object under {@code id}. Any texture
     * currently occupying that slot is {@link AbstractTexture#close() closed}
     * exactly once before replacement (unless it is the same instance) — the
     * 1.20.4 replacement for legacy {@code deleteGlTexture()}.
     *
     * <p>The shared missing-sprite sentinel is never closed (legacy's
     * {@code texture != TextureUtil.MISSING_TEXTURE} guard, and the same guard
     * vanilla's own {@code registerTexture} uses): {@code TextureManager} parks
     * it under any identifier whose load failed, so a slot being replaced can
     * legitimately hold it — e.g. the P231 GIF pipeline re-registering a
     * {@code .gif} whose first (placeholder) load did not decode.</p>
     */
    public void register(Identifier id, AbstractTexture texture)
    {
        AbstractTexture old = this.textures.get(id);

        if (old != null && old != texture && old != missingTexture())
        {
            old.close();
        }

        this.textures.put(id, texture);
    }

    /**
     * Legacy {@code remove()}: drop the mapping and release its GL/native
     * resources.
     */
    public void delete(Identifier id)
    {
        AbstractTexture old = this.textures.remove(id);

        if (old != null)
        {
            old.close();
        }
    }

    /**
     * Legacy {@code replace(String)}: alias {@code from} to whatever texture
     * object {@code to} <em>already</em> resolves to. If {@code to} is not
     * registered the call is a silent no-op (legacy {@code if (texture != null)}),
     * and no texture is closed — the two identifiers deliberately share one
     * object afterwards.
     */
    public void alias(Identifier from, Identifier to)
    {
        AbstractTexture texture = this.textures.get(to);

        if (texture != null)
        {
            this.textures.put(from, texture);
        }
    }

    /**
     * Legacy {@code setLinear(boolean)} filter matrix. Returns
     * {@code {minFilter, magFilter}} GL enums for the requested
     * (linear, mipmap) combination:
     *
     * <ul>
     *   <li>min = linear ? (mipmap ? LINEAR_MIPMAP_LINEAR : LINEAR)
     *                    : (mipmap ? NEAREST_MIPMAP_LINEAR : NEAREST)</li>
     *   <li>mag = linear ? LINEAR : NEAREST</li>
     * </ul>
     *
     * Pure — the load-bearing decision the P140 toggle depends on.
     */
    public static int[] filterEnums(boolean linear, boolean mipmap)
    {
        int min = linear
            ? (mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR)
            : (mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST);
        int mag = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;

        return new int[] {min, mag};
    }

    /**
     * Apply the {@link #filterEnums} matrix to a registered texture's GL object
     * (source-review-only GL boundary; exceptions swallowed as legacy did).
     */
    public void setFilter(Identifier id, boolean linear, boolean mipmap)
    {
        AbstractTexture texture = this.textures.get(id);

        if (texture == null)
        {
            return;
        }

        int[] filter = filterEnums(linear, mipmap);

        try
        {
            RenderSystem.bindTexture(texture.getGlId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter[0]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter[1]);
        }
        catch (Exception e)
        {
            /* Legacy swallowed GL state errors here */
        }
    }

    /**
     * Legacy {@code export()} — writes the texture behind {@code id} to a PNG in
     * {@code folder} using the {@link TextureUtils#getFirstAvailableFile}
     * naming contract. Textures we own (a {@link NativeImageBackedTexture})
     * export straight from the CPU-side {@link NativeImage}; foreign textures
     * fall back to a GL {@code glGetTexImage} readback + the legacy
     * RGBA&rarr;ARGB repack.
     *
     * @return the written file, or {@code null} on failure (total — never throws)
     */
    public File export(Identifier id, File folder, String baseName)
    {
        AbstractTexture texture = this.textures.get(id);

        if (texture == null)
        {
            return null;
        }

        try
        {
            BufferedImage image;

            if (texture instanceof NativeImageBackedTexture)
            {
                image = toBufferedImage(((NativeImageBackedTexture) texture).getImage());
            }
            else
            {
                image = readBackFromGl(texture);
            }

            return writeImage(image, folder, baseName);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Pure helper (headless-testable): write {@code image} as a PNG into
     * {@code folder} at the first available legacy-named slot.
     */
    public static File writeImage(BufferedImage image, File folder, String baseName) throws Exception
    {
        folder.mkdirs();

        File file = TextureUtils.getFirstAvailableFile(folder, baseName);

        ImageIO.write(image, "png", file);

        return file;
    }

    private static BufferedImage toBufferedImage(NativeImage source)
    {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                /* NativeImage.getColor packs ABGR little-endian; repack to ARGB */
                int abgr = source.getColor(x, y);
                int a = (abgr >> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;

                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return image;
    }

    private static BufferedImage readBackFromGl(AbstractTexture texture)
    {
        RenderSystem.bindTexture(texture.getGlId());

        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);

        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        byte[] rgba = new byte[w * h * 4];
        buffer.get(rgba);

        int[] argb = TextureUtils.argbFromRgba(rgba, w, h);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        image.setRGB(0, 0, w, h, argb, 0, w);

        return image;
    }
}
