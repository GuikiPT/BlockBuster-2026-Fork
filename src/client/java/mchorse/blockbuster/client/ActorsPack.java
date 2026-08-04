package mchorse.blockbuster.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.mclib.GifFrameFile;
import mchorse.mclib.utils.resources.VerbatimPaths;

/**
 * Actors pack resolver (roadmap P88).
 *
 * <p>1.12.2's {@code mchorse.blockbuster.client.ActorsPack} was an
 * {@code IResourcePack} appended to FML's resource-pack list; it served the
 * {@code b.a} (skins on disk), {@code http} and {@code https} (URL skins)
 * namespaces by walking {@code Blockbuster.proxy.pack.folders} (the ModelPack
 * folder list). 1.20.4 has no pluggable-resource-pack seam of the same shape, so
 * the class survives as a plain <b>resolver</b>: it maps a verbatim
 * {@code domain:path} to a {@link File}/{@link InputStream}, and the
 * {@code ReloadableResourceManagerImpl} mixin
 * ({@code ReloadableResourceManagerImplMixin}) routes matching identifiers here
 * at the resource-manager boundary — the same seam McLib's multiskin ASM coremod
 * used.</p>
 *
 * <p>The folder-walk order, the {@code .gif>/} frame-path grammar and the
 * exists→open {@link #lastFile} cache are preserved byte-for-byte from legacy.
 * The GIF decode/upload machinery ({@code GifTexture}/{@code GifProcessThread})
 * and the URL download path are client-only and attach through the
 * {@link #gifFileFactory}, {@link #gifHandler} and {@link #urlSkinResolver}
 * seams — all three installed by
 * {@code mchorse.blockbuster.client.textures.SkinPipelineWiring} (P231/P232).
 * This class itself must stay free of {@code MinecraftClient}/GL imports:
 * {@code ActorsPackResolveTest} exercises it without a game.</p>
 *
 * <h3>The sanitized {@code '>'} problem (P231)</h3>
 *
 * <p>Legacy's virtual-folder sentinel is a literal {@code '>'}
 * ({@code anim.gif>/frame3.png}), but 1.20.4's {@link net.minecraft.util.Identifier}
 * forbids it and {@code ResourceLocation.sanitizePath} maps it to {@code '_'}.
 * Every gif-frame lookup therefore arrives here spelled {@code anim.gif_/frame3.png}
 * and would miss the {@code ".gif>/"} gate. {@link #unsanitizeGifPath} restores the
 * legacy spelling at this boundary, which keeps {@code GifFrameFile}/{@code GifFolder}
 * byte-for-byte legacy ports.</p>
 *
 * <p><b>The mapping is not injective</b> and the port accepts the collision: a real
 * on-disk directory literally named {@code foo.gif_} is indistinguishable from the
 * sanitized form of {@code foo.gif>} and will be read as a gif pseudo-folder,
 * because {@code .gif_/} is not a name any real pack uses.</p>
 *
 * <h3>Case and spaces (P249, defect E)</h3>
 *
 * <p>The other half of the same lossiness — {@code MySkin.png} arriving as
 * {@code myskin.png}, {@code my skin.png} as {@code my_skin.png} — is <b>not</b>
 * accepted, because those are names real packs use constantly and on a
 * case-sensitive filesystem every one of them missed. That is what S07 open
 * question 1's verbatim side table is for: {@link VerbatimPaths} is populated by
 * {@code ResourceLocation.toIdentifier()} and consulted by
 * {@link #findFile(String, String)}. See that method and the
 * {@code VerbatimPaths} javadoc for why a candidate list beats an injective
 * escape grammar here.</p>
 */
public class ActorsPack
{
    /**
     * Legacy {@code GifFrameNSPattern}: matches Optifine {@code _n}/{@code _s}
     * normal/specular shader-map frame requests inside a gif pseudo-directory.
     */
    private static final Pattern GifFrameNSPattern = Pattern.compile("^(.*)\\.gif>\\/frame(\\d+)(_n|_s)\\.png$");

    public static final String DOMAIN = "b.a";
    public static final String HTTP = "http";
    public static final String HTTPS = "https";

    /** Shared resolver instance the resource-manager mixin routes through. */
    public static final ActorsPack INSTANCE = new ActorsPack();

    /**
     * SEAM(P68 — ModelPack): the lookup-folder list legacy read from
     * {@code Blockbuster.proxy.pack.folders}. Until ModelPack lands, this
     * defaults to the single config models folder ({@code config/blockbuster/
     * models}). Overridable so ModelPack can supply the full ordered list
     * (config models + world models + the {@code model_folders.path} extra) and
     * so headless tests can inject temp folders. First folder wins.
     */
    public static Supplier<List<File>> folders = () -> Collections.singletonList(BlockbusterPaths.models().toFile());

    /**
     * SEAM(P89/P231 — GIF): builds the virtual {@code GifFrameFile} for a
     * {@code ".gif>/frameN.png"} verbatim child under {@code folder}. Installed
     * by {@code SkinPipelineWiring}; {@code null} here means GIF-frame paths
     * cannot be materialised and therefore miss.
     */
    public static GifFileFactory gifFileFactory;

    /**
     * SEAM(P231 — GIF): the <i>scheduling</i> half of legacy
     * {@code ActorsPack.getInputStream} ({@code handleGif}). Legacy did two
     * things when it served a gif (or a frame of one): return the bytes
     * <b>and</b> kick {@code GifProcessThread.create} so the animated texture
     * gets built. The port keeps only the byte half here — the scheduling half
     * and the GL/{@code MinecraftClient} it needs live behind this seam, in the
     * client-only installer.
     */
    public static GifHandler gifHandler;

    /**
     * SEAM(P90/P232 — URL skins): resolves an {@code http}/{@code https} skin
     * URL to a stream (synchronous download or async spawn + black-pixel
     * placeholder). Installed by {@code SkinPipelineWiring}; while {@code null}
     * the {@code http}/{@code https} namespaces are not intercepted and fall
     * through to vanilla.
     */
    public static UrlSkinResolver urlSkinResolver;

    /**
     * Cached last file from {@link #has(String, String)}, consumed by the
     * following {@link #open(String, String)} — mirrors legacy
     * {@code ActorsPack.lastFile}, the exists→open single-entry cache.
     */
    private File lastFile;

    /* Seam interfaces (all installed by SkinPipelineWiring, P231/P232) */

    @FunctionalInterface
    public interface GifFileFactory
    {
        /** @return a {@code GifFrameFile} (a {@link File} subclass) or {@code null} if it can't exist. */
        File create(File folder, String verbatimChild);
    }

    @FunctionalInterface
    public interface UrlSkinResolver
    {
        /** @return a skin stream, or {@code null} to fall back. */
        InputStream open(String domain, String path);
    }

    @FunctionalInterface
    public interface GifHandler
    {
        /**
         * Called whenever a resolved file is a {@code .gif} (or a virtual frame
         * of one) that is about to be streamed.
         *
         * @param domain  the request namespace ({@code b.a})
         * @param gifPath the verbatim path of the <b>gif itself</b> — for a frame
         *                request this is the request path truncated at its last
         *                {@code '>'}, exactly as legacy computed it
         * @param file    the resolved file; a {@code GifFrameFile} for a frame
         *                request (its own {@link File} path is already the
         *                {@code .gif}), otherwise the plain {@code .gif}
         * @return a stream to serve <i>instead of</i> the raw gif bytes, or
         *         {@code null} to fall back to the legacy raw-bytes behaviour
         */
        InputStream handle(String domain, String gifPath, File file);
    }

    /**
     * Restore the legacy {@code '>'} virtual-folder sentinel that
     * {@code ResourceLocation.sanitizePath} turned into {@code '_'} on the way
     * into an {@link net.minecraft.util.Identifier}. See the class javadoc for
     * why the non-injectivity is accepted.
     */
    public static String unsanitizeGifPath(String path)
    {
        return path.indexOf(".gif_/") == -1 ? path : path.replace(".gif_/", ".gif>/");
    }

    private static List<File> folderList()
    {
        List<File> list = folders.get();

        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Whether a {@code domain:path} is one of the actor-pack namespaces
     * ({@code b.a}, or — only once {@link #urlSkinResolver} is installed —
     * {@code http}/{@code https}). The mixin uses this to decide whether to
     * intercept a resource-manager lookup.
     */
    public static boolean handles(String domain, String path)
    {
        if (domain.equals(DOMAIN))
        {
            return true;
        }

        return isUrl(domain, path) && urlSkinResolver != null;
    }

    private static boolean isUrl(String domain, String path)
    {
        return (domain.equals(HTTP) || domain.equals(HTTPS)) && path.startsWith("//") && !path.endsWith(".mcmeta");
    }

    /**
     * Resolve the on-disk {@link File} for a {@code b.a} path by walking the
     * lookup folders in order (first existing wins). Returns {@code null} on a
     * miss. Never touches the {@link #lastFile} cache — this is the stateless
     * entry point the mixin's single-call path prefers.
     */
    public File findFile(String path)
    {
        return this.findFile(DOMAIN, path);
    }

    /**
     * P249 (defect E) — the same walk, but the sanitized path is first mapped
     * back through the {@link VerbatimPaths} side table.
     *
     * <p>Identifiers are lowercase-only, so {@code MySkin.png} and
     * {@code my skin.png} arrive here spelled {@code myskin.png} /
     * {@code my_skin.png} and {@code new File(folder, path)} misses on every
     * case-sensitive filesystem. Legacy had the original string (mclib's
     * {@code TextureLocation} reflection hack) and used it verbatim. The side
     * table restores that: every spelling recorded for this identifier is tried
     * in registration order, then — always — the sanitized path itself, so
     * lookups that never went through {@code toIdentifier()} behave exactly as
     * before.</p>
     */
    public File findFile(String domain, String path)
    {
        for (String verbatim : VerbatimPaths.candidates(domain, path))
        {
            File file = this.walk(verbatim);

            if (file != null)
            {
                return file;
            }
        }

        return this.walk(path);
    }

    private File walk(String path)
    {
        String verbatim = unsanitizeGifPath(path);

        for (File folder : folderList())
        {
            File packFile = this.buildPackFile(folder, verbatim);

            if (packFile != null && packFile.exists())
            {
                return packFile;
            }
        }

        return null;
    }

    private File buildPackFile(File folder, String path)
    {
        if (path.contains(".gif>/"))
        {
            Matcher matcher = GifFrameNSPattern.matcher(path);

            if (matcher.find())
            {
                String pathPart = matcher.group(1);
                String index = matcher.group(2);
                String type = matcher.group(3);

                File gifNS = new File(folder, pathPart + type + ".gif");

                if (gifNS.exists())
                {
                    /* SEAM(P89/P231): GifFrameFile wraps the _n/_s gif. */
                    GifFileFactory factory = gifFileFactory;

                    return factory == null ? null : factory.create(folder, pathPart + type + ".gif>/frame" + index + ".png");
                }
                else
                {
                    /* This is what Optifine will do without this mod. */
                    return new File(folder, pathPart + ".gif" + type + ".png");
                }
            }
            else
            {
                /* SEAM(P89/P231): a plain gif-frame request → GifFrameFile. */
                GifFileFactory factory = gifFileFactory;

                return factory == null ? null : factory.create(folder, path);
            }
        }

        return new File(folder, path);
    }

    /**
     * Legacy {@code resourceExists}: caches the resolved file in
     * {@link #lastFile} for the following {@link #open(String, String)}. URL
     * namespaces report present unconditionally (once {@link #urlSkinResolver}
     * is installed).
     */
    public boolean has(String domain, String path)
    {
        if (isUrl(domain, path))
        {
            return urlSkinResolver != null;
        }

        if (!domain.equals(DOMAIN))
        {
            return false;
        }

        File file = this.findFile(domain, path);

        this.lastFile = file;

        return file != null;
    }

    /**
     * Legacy {@code getInputStream}: serves the resolved file's stream. Uses the
     * {@link #lastFile} cache if a preceding {@link #has(String, String)}
     * populated it, otherwise re-walks. URL namespaces go through the P90 seam
     * (falling through to a miss while it is uninstalled).
     */
    public InputStream open(String domain, String path) throws IOException
    {
        if (isUrl(domain, path))
        {
            UrlSkinResolver resolver = urlSkinResolver;

            if (resolver != null)
            {
                InputStream stream = resolver.open(domain, path);

                if (stream != null)
                {
                    return stream;
                }
            }

            throw new FileNotFoundException(domain + ":" + path);
        }

        File file = this.lastFile;

        if (file == null)
        {
            file = this.findFile(domain, path);
        }

        if (file != null)
        {
            this.lastFile = null;

            return this.openFile(domain, path, file);
        }

        throw new FileNotFoundException(domain + ":" + path);
    }

    /**
     * Stream a concrete resolved file. Used by the resource-manager mixin's
     * lazy {@code InputSupplier} (stateless — it captures the {@link File}
     * up-front instead of relying on the {@link #lastFile} cache, so concurrent
     * lookups can't clobber it).
     *
     * <p>This is legacy {@code getInputStream}'s tail, which did <b>two</b>
     * things for gifs: returned the bytes and scheduled
     * {@code GifProcessThread.create} through {@code handleGif}. The scheduling
     * half is the {@link #gifHandler} seam (it needs the client); the request
     * {@code domain}/{@code path} are carried in because the location the gif
     * is registered under is derived from the request, not from the file —
     * legacy truncated the request path at its <b>last</b> {@code '>'}, so an
     * Optifine {@code _n} frame request schedules the <i>base</i> gif. Quirk
     * preserved.</p>
     *
     * <p>A resolved {@code GifFrameFile}'s own {@link File} path is already the
     * {@code .gif} (its super-path is truncated at the {@code .gif} boundary),
     * so both gif branches stream the same underlying file — legacy's "a frame
     * request serves the whole gif" behaviour. Do not "fix" it.</p>
     */
    public InputStream openFile(String domain, String path, File file) throws IOException
    {
        GifHandler handler = gifHandler;

        if (handler != null)
        {
            String verbatim = unsanitizeGifPath(path);
            String gifPath = null;

            if (file instanceof GifFrameFile)
            {
                /* Legacy: gifPath = path.substring(0, path.lastIndexOf('>')).
                 * The literal "<name>.gif_n.png" Optifine fallback is NOT a
                 * GifFrameFile, so it correctly never lands here. */
                int index = verbatim.lastIndexOf('>');

                gifPath = index == -1 ? null : verbatim.substring(0, index);
            }
            else if (verbatim.toLowerCase().endsWith(".gif"))
            {
                gifPath = verbatim;
            }

            if (gifPath != null)
            {
                InputStream stream = handler.handle(domain, gifPath, file);

                if (stream != null)
                {
                    return stream;
                }
            }
        }

        return new FileInputStream(file);
    }
}
