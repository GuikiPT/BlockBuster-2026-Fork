package mchorse.blockbuster.client.textures;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;

import mchorse.blockbuster.Blockbuster;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * URL download thread (P90)
 *
 * <p>Port of 1.12.2's {@code mchorse.blockbuster.client.textures.URLDownloadThread}.
 * This bad boy downloads a picture from the internet and puts it into the
 * texture manager. The legacy class name/fields are preserved for diff-ability.</p>
 *
 * <p>The URL-recognition ({@link #isURL}), download ({@link #downloadImage}) and
 * branch-decision ({@link #handleURLSkins}) surface is pure logic and headless-
 * testable against a local HTTP stub. Only {@link #addToManager} and the async
 * render-thread hop touch GL / the client; both are guarded so the rest is
 * exercisable without a game client.</p>
 *
 * <p><b>Parity note (P90):</b> legacy's async path did
 * {@code new Thread(new URLDownloadThread(location)).start()}, whose {@code run()}
 * merely wrapped the whole download in
 * {@code Minecraft.getMinecraft().addScheduledTask(...)} — network I/O executed on
 * the render thread even in "async" mode. Spawning that extra thread is pointless
 * (it immediately hopped to the render thread and did nothing off-thread), so the
 * port collapses to scheduling the download+register directly on the client
 * executor ({@code MinecraftClient.execute}, the modern {@code addScheduledTask}).
 * Behaviourally identical to legacy; all failures stay silent (empty catch).</p>
 */
@Environment(EnvType.CLIENT)
public class URLDownloadThread implements Runnable
{
    /**
     * Look, MA! I'm Google Chrome on OS X!!! xD
     */
    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36";

    /**
     * Jar resource returned as the black-pixel fallback whenever a URL skin
     * fails to download (legacy {@code ActorsPack.hanldeURLSkins}). The asset is
     * carried over from 1.12.2 (S0 asset port).
     */
    public static final String FALLBACK = "/assets/blockbuster/textures/blocks/black.png";

    /**
     * SEAM(P88): render-thread executor for the async download branch. When
     * {@code null}, the live {@link MinecraftClient} is used (its
     * {@code execute(Runnable)} is 1.12.2's {@code addScheduledTask}). Headless
     * tests set this to a manual/collecting executor to pump the deferred task
     * without a client.
     */
    public static Executor renderExecutor;

    private final ResourceLocation url;

    public URLDownloadThread(ResourceLocation url)
    {
        this.url = url;
    }

    public ResourceLocation getUrl()
    {
        return this.url;
    }

    /**
     * URL-skin recognition — a resource location is a URL skin iff its domain is
     * {@code http}/{@code https}, its path starts with {@code "//"} and it is not
     * {@code .mcmeta} metadata. Matches legacy {@code ActorsPack.getInputStream}
     * / {@code resourceExists}. The RL string {@code https://host/skin.png} parses
     * as domain {@code https}, path {@code //host/skin.png}.
     */
    public static boolean isURL(ResourceLocation location)
    {
        String domain = location.getResourceDomain();
        String path = location.getResourcePath();

        return (domain.equals("http") || domain.equals("https")) && path.startsWith("//") && !path.endsWith(".mcmeta");
    }

    /**
     * Download the image at the given URL location. Pure I/O; total against the
     * network but not the disk contract:
     *
     * <ul>
     *   <li>sends the legacy Chrome/macOS {@link #USER_AGENT};</li>
     *   <li><b>rejects</b> (returns {@code null}) a response whose
     *       {@code Content-Type} header is present and does not
     *       {@code startsWith("image/")};</li>
     *   <li>a <b>missing</b> {@code Content-Type} header passes;</li>
     *   <li>no redirect/timeout tuning (URLConnection defaults) — matches legacy.</li>
     * </ul>
     */
    public static InputStream downloadImage(final ResourceLocation url) throws IOException
    {
        URLConnection con = new URL(url.toString()).openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);

        InputStream stream = con.getInputStream();
        String type = con.getHeaderField("Content-Type");

        if (type != null && !type.startsWith("image/"))
        {
            return null;
        }

        return stream;
    }

    /**
     * Resolve a URL skin, choosing synchronous-in-resolve vs. deferred download
     * from {@code Blockbuster.syncedURLTextureDownload}. Port of legacy
     * {@code ActorsPack.hanldeURLSkins}:
     *
     * <ul>
     *   <li><b>sync</b> ({@code url_skins_sync_download == true}, default):
     *       download inside the resolve; a {@code null} stream (Content-Type
     *       gate) throws {@code "Couldn't download image..."}, caught locally;</li>
     *   <li><b>async</b> ({@code false}): schedule the download+register on the
     *       render executor and return the fallback immediately;</li>
     *   <li>either failure path returns the bundled black-pixel jar resource.</li>
     * </ul>
     *
     * <p>This is the P88 {@code ActorsPack} resolver's URL branch, kept here in
     * P90 so the decision logic is self-contained and unit-tested. P88's resolver
     * delegates to it.</p>
     */
    public static InputStream handleURLSkins(ResourceLocation location)
    {
        try
        {
            if (Blockbuster.syncedURLTextureDownload.get())
            {
                InputStream stream = downloadImage(location);

                if (stream == null)
                {
                    throw new IOException("Couldn't download image...");
                }

                return stream;
            }
            else
            {
                schedule(new URLDownloadThread(location));
            }
        }
        catch (IOException e)
        {}

        /* Make it a black pixel in case it fails */
        return fallbackStream();
    }

    /**
     * The bundled black-pixel jar resource (legacy fallback).
     */
    public static InputStream fallbackStream()
    {
        return URLDownloadThread.class.getResourceAsStream(FALLBACK);
    }

    /**
     * Decode the downloaded body and register it into the texture manager keyed
     * by the URL location. Legacy: {@code ImageIO.read} → {@code SimpleTexture} +
     * {@code TextureUtil.uploadTextureImageAllocate} → reflected into the
     * {@code TextureManager} map. The port decodes to a {@link NativeImage} and
     * registers a {@link NativeImageBackedTexture}.
     *
     * <p>SEAM(P87): the close-on-replace texture registry lands with P87; until
     * then this registers directly through the vanilla {@code TextureManager}
     * (the modern equivalent of legacy's reflected map insert). The exact
     * RL→Identifier keying contract for URL skins is owned by P87/P88 — the
     * {@code toIdentifier()} conversion here is a placeholder consistent with the
     * resolver boundary. Requires the render thread / GL context, so it is not
     * headlessly unit-tested.</p>
     */
    public static void addToManager(ResourceLocation url, InputStream is) throws IOException
    {
        NativeImage image = NativeImage.read(is);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);

        MinecraftClient.getInstance().getTextureManager().registerTexture(url.toIdentifier(), texture);
    }

    /**
     * The deferred download task: download and register on the render thread.
     * (Legacy {@code run()} did the same via {@code addScheduledTask}; here the
     * render-thread hop already happened in {@link #schedule}, so the body runs
     * directly.) All {@code IOException}s are silently swallowed.
     */
    @Override
    public void run()
    {
        try
        {
            InputStream stream = downloadImage(this.url);

            if (stream != null)
            {
                addToManager(this.url, stream);
            }
        }
        catch (IOException e)
        {}
    }

    /**
     * Schedule a task on the render executor (or the configured test executor).
     * With no executor and no live client (headless production — never), the task
     * is dropped.
     */
    private static void schedule(Runnable task)
    {
        if (renderExecutor != null)
        {
            renderExecutor.execute(task);

            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null)
        {
            client.execute(task);
        }
    }
}
