package mchorse.blockbuster.client.video;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Transparent still screenshots (roadmap S18 P204 — one-key alpha screenshot).
 *
 * <p>A pure port addition: 1.12.2 Blockbuster/McLib/Aperture shipped <b>no</b>
 * screenshot code (verified by source grep — no {@code ScreenshotHelper} /
 * {@code glReadPixels}-for-stills anywhere), so behavior is defined by the
 * roadmap ("remove opacity things when screenshot") and by S3 <b>P46</b>, not
 * by a legacy diff. Two variants, both landing an RGBA PNG through the shared
 * P46 encode path ({@link mchorse.mclib.client.ScreenshotCapture}):</p>
 *
 * <ul>
 *   <li><b>World variant</b> — a keybind ({@link ScreenshotKeyHandler}) queues a
 *       one-frame request; the render hook grabs the main framebuffer's colour
 *       attachment (alpha preserved) after the world draws and before the HUD,
 *       then writes {@code screenshots/blockbuster/<timestamp>.png}. Alpha is
 *       only <i>meaningful</i> when the P203 {@code green_screen_sky} discipline
 *       is active for that frame — v1 scope is <b>sky-only transparency</b>
 *       (documented; force-alpha-per-frame is a batch-4 seam against P203).</li>
 *   <li><b>Model-editor variant</b> — reuses P46's offscreen alpha render target
 *       ({@code mclib.ScreenshotCapture.capture}) so the model draws over a fully
 *       transparent clear; the file is prefixed with the model name
 *       ({@code <model>_<timestamp>.png}) to keep editor captures distinct. Its
 *       trigger is an <b>F2</b> keybind registered on
 *       {@code GuiModelEditorPanel.keys()} (the S3 framework's per-element
 *       keybind manager, so it also shows in the F9 overlay); the key raises a
 *       one-shot flag that the panel's {@code draw} consumes, because the
 *       capture has to run on the render thread.</li>
 * </ul>
 *
 * <p><b>S22 P298 — the config surface.</b> P204 shipped with no config at all and
 * an unbound keybind, so the feature was effectively unreachable and the
 * Blockbuster settings screen never mentioned it. The {@code screenshot}
 * category ({@link ScreenshotConfig}) adds the feature switch this class reads in
 * {@link #requestWorldCapture()}, the vanilla-F2 takeover
 * ({@code ScreenshotRecorderMixin}), and the output-folder override
 * {@link #getScreenshotsDir()} honours.</p>
 *
 * <p>Output lands in {@code <run dir>/screenshots/blockbuster/} (or
 * {@code screenshot.export_path}) rather than the vanilla {@code screenshots/}
 * root, so alpha stills never collide with plain F2 captures. Naming reuses the P202 timestamp pattern
 * {@code yyyy-MM-dd_HH.mm.ss} for consistency across the recorder, with the same
 * {@code _N} de-duplication scheme vanilla {@code ScreenshotRecorder} uses when
 * two captures share a second.</p>
 *
 * <p>All naming / encode helpers are pure and headless-testable; only the two
 * GL-facing capture entry points touch the render thread.</p>
 */
public class ScreenshotCapture
{
    /** Sub-folder under the vanilla {@code screenshots/} dir for alpha stills. */
    public static final String SUBFOLDER = "blockbuster";

    /** Shared with P202's recorder filename ghost text (yyyy-MM-dd_HH.mm.ss). */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    /**
     * Keybind → next-frame handshake. The key sets this on the client tick; the
     * world-render hook consumes it once so exactly one frame is captured per
     * press (kept off the GUI so the HUD/menu is never in the shot).
     */
    private static final AtomicBoolean WORLD_REQUEST = new AtomicBoolean(false);

    /**
     * S22 <b>P298</b> — the optional completion sink for the pending world
     * capture, set alongside {@link #WORLD_REQUEST} and consumed with it.
     *
     * <p>Its one production user is the vanilla-F2 takeover: cancelling
     * {@code ScreenshotRecorder.saveScreenshot} also cancels vanilla's "Saved
     * screenshot as …" chat line, and a hijacked key that reports nothing looks
     * broken. The mixin hands its own {@code Consumer<Text>} down as a
     * {@code Consumer<File>} callback, which fires on the IO worker right after
     * the PNG is written — the same thread and the same moment vanilla
     * {@code ScreenshotRecorder} calls its message receiver from.</p>
     */
    private static final AtomicReference<Consumer<File>> WORLD_NOTIFIER =
        new AtomicReference<>();

    /* Request state machine (pure — drives the world variant) */

    /** Queue a one-frame world capture for the next rendered frame. */
    public static void requestWorldCapture()
    {
        requestWorldCapture(null);
    }

    /**
     * Queue a one-frame world capture, reporting the written file to
     * {@code onWritten} (may be {@code null}) from the IO worker.
     *
     * <p><b>S22 P298 — this is the feature gate.</b> Both triggers of the world
     * variant funnel through here ({@link ScreenshotKeyHandler}'s keybind poll
     * and {@code ScreenshotRecorderMixin}'s F2 takeover), so consulting the
     * <b>live</b> {@code screenshot.transparent} value once at the queue point is
     * what makes the config option real: with it off no request is ever queued,
     * and both readback seams stay no-ops without either of them having to know
     * about the setting. Read through {@link ScreenshotConfig#transparent()} —
     * never the folded {@code DEFAULT_TRANSPARENT} constant (S22/P250).</p>
     */
    public static void requestWorldCapture(Consumer<File> onWritten)
    {
        if (!ScreenshotConfig.transparent())
        {
            return;
        }

        WORLD_NOTIFIER.set(onWritten);
        WORLD_REQUEST.set(true);
    }

    /** True at most once per {@link #requestWorldCapture()} (clears the flag). */
    public static boolean consumeWorldRequest()
    {
        return WORLD_REQUEST.getAndSet(false);
    }

    /** Whether a world capture is pending (does not clear the flag). */
    public static boolean isWorldRequestPending()
    {
        return WORLD_REQUEST.get();
    }

    /**
     * S21 <b>P272.1</b> — take the pending world capture, but only from the seam
     * that owns the readback on this install.
     *
     * <p>Without a shader pack that is {@code WorldRenderEvents.LAST}, exactly as
     * before. With one it is {@code GameRenderer.renderWorld} RETURN, because at
     * {@code LAST} the pack has not composited into the main colour texture yet
     * and the still would be an unshaded frame — see
     * {@link ShaderPackVideoCompat}. The request flag is only consumed by the
     * owning seam, so a press is never eaten by the wrong one.</p>
     */
    public static void captureWorldFrameAt(ShaderPackVideoCompat.Readback point)
    {
        if (!ShaderPackVideoCompat.readbackAt(point))
        {
            return;
        }

        if (!consumeWorldRequest())
        {
            return;
        }

        warnIfAlphaIsMeaningless();
        captureWorldFrame();
    }

    /** One-shot latch for {@link #warnIfAlphaIsMeaningless()}. */
    private static boolean warnedAlpha;

    /**
     * P272.1: the still's whole point is the alpha channel, and under a shader
     * pack the pack's {@code final} program owns it (the P203 chroma sky that
     * would have written a transparent sky is itself disabled under a pack). Say
     * so once rather than shipping an opaque PNG that looks like a bug.
     */
    static void warnIfAlphaIsMeaningless()
    {
        if (ShaderPackVideoCompat.isAlphaMeaningful() || warnedAlpha)
        {
            return;
        }

        warnedAlpha = true;

        Blockbuster.LOGGER.warn(
            "Transparent screenshot taken while a shader pack is in use — the pack's final program writes the "
                + "alpha channel and the green-screen sky is disabled under a pack, so this PNG will be opaque.");
    }

    /** Test hygiene (P272.1): drop the one-shot alpha warning latch. */
    public static void resetWarnings()
    {
        warnedAlpha = false;
    }

    /* Naming / output conventions (pure — headless-testable) */

    /** Current wall-clock timestamp in the shared recorder format. */
    public static String timestamp()
    {
        return DATE_FORMAT.format(new Date());
    }

    /**
     * Model-name → filesystem-safe prefix component: strip path separators and
     * characters illegal on common filesystems, collapse blanks to
     * {@code "model"} so a nameless model still produces a valid file.
     */
    public static String sanitizeModelName(String name)
    {
        if (name == null)
        {
            return "model";
        }

        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");

        /* Trim leading/trailing separators the collapse may have produced. */
        cleaned = cleaned.replaceAll("^_+|_+$", "");

        return cleaned.isEmpty() ? "model" : cleaned;
    }

    /**
     * Resolve a non-colliding PNG under {@code dir} named
     * {@code <prefix><base>[_N].png}: the first free {@code N} starting at the
     * unsuffixed name (matches vanilla {@code ScreenshotRecorder}'s scheme).
     * Pure — the caller supplies {@code base} so a fixed timestamp is testable.
     */
    public static File resolveFile(File dir, String prefix, String base)
    {
        String head = (prefix == null ? "" : prefix) + base;
        int i = 1;

        while (true)
        {
            File file = new File(dir, head + (i == 1 ? "" : "_" + i) + ".png");

            if (!file.exists())
            {
                return file;
            }

            i++;
        }
    }

    /** {@code <dir>/<timestamp>.png} (deduplicated) for a world capture. */
    public static File worldFile(File dir)
    {
        dir.mkdirs();

        return resolveFile(dir, "", timestamp());
    }

    /** {@code <dir>/<model>_<timestamp>.png} (deduplicated) for an editor capture. */
    public static File modelFile(File dir, String modelName)
    {
        dir.mkdirs();

        return resolveFile(dir, sanitizeModelName(modelName) + "_", timestamp());
    }

    /**
     * The alpha-still output directory: {@code screenshot.export_path} when the
     * user set one, else P204's {@code <run dir>/screenshots/blockbuster}.
     * Falls back to a working-dir path when there is no client (headless).
     *
     * <p>S22 P298: the override reads the <b>live</b> config value through
     * {@link ScreenshotConfig#exportPath()} and follows {@code video.export_path}'s
     * rules verbatim ({@code MinemaBackend.moviesDir}) — trimmed, and a blank
     * string means "the default", so clearing the box in the settings screen
     * restores it rather than writing into the run directory root. Both variants
     * go through here, so the model-editor capture honours it too.</p>
     */
    public static File getScreenshotsDir()
    {
        String path = ScreenshotConfig.exportPath();

        if (path != null && !path.trim().isEmpty())
        {
            return new File(path.trim());
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        File run = mc != null ? mc.runDirectory : new File(".");

        return new File(new File(run, "screenshots"), SUBFOLDER);
    }

    /* Encode helper (pure — no GL; shares the P46 writer) */

    /**
     * Flip a bottom-up GL RGBA readback and write it as an RGBA PNG off-thread.
     * Alpha is preserved (incl. 0 / partial). Extracted from the GL grab so the
     * flip + encode is unit-testable at arbitrary (incl. odd) dimensions.
     */
    public static void encodeAndWrite(ByteBuffer rgba, int width, int height, File destination)
    {
        encodeAndWrite(rgba, width, height, destination, null);
    }

    /**
     * As {@link #encodeAndWrite(ByteBuffer, int, int, File)}, but reporting the
     * written file to {@code onWritten} (may be {@code null}) on success, from the
     * IO worker — P298's chat-feedback hook for the vanilla-F2 takeover. A failed
     * write logs and reports nothing, so a hijacked F2 never claims a file that
     * is not there.
     */
    public static void encodeAndWrite(ByteBuffer rgba, int width, int height, File destination,
        Consumer<File> onWritten)
    {
        final int[] pixels = mchorse.mclib.client.ScreenshotCapture.rgbaToArgbFlipped(rgba, width, height);

        Util.getIoWorkerExecutor().execute(() ->
        {
            try
            {
                mchorse.mclib.client.ScreenshotCapture.writePng(pixels, width, height, destination);

                if (onWritten != null)
                {
                    onWritten.accept(destination);
                }
            }
            catch (IOException e)
            {
                Blockbuster.LOGGER.error("Failed to write transparent screenshot {}", destination, e);
            }
        });
    }

    /* GL-facing capture entry points (render thread only; classload headless) */

    /**
     * World variant: read back the main framebuffer's colour attachment (alpha
     * preserved) and write {@code screenshots/blockbuster/<timestamp>.png}.
     *
     * <p>Must run after the world render and before the HUD (Fabric
     * {@code WorldRenderEvents.LAST}) so no GUI is in the frame. The framebuffer
     * has an alpha channel; whether that alpha is transparent depends on the
     * P203 green-screen-sky discipline being active for the frame (v1 scope:
     * sky-only transparency — see class javadoc).</p>
     */
    public static void captureWorldFrame(File destination)
    {
        captureWorldFrame(destination, null);
    }

    /** As {@link #captureWorldFrame(File)}, reporting the written file (P298). */
    public static void captureWorldFrame(File destination, Consumer<File> onWritten)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getFramebuffer() == null)
        {
            return;
        }

        Framebuffer framebuffer = mc.getFramebuffer();
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);

        /* Read straight from the framebuffer's colour texture — same technique
         * as the P200 PBO path, minus the async ring (a single still is fine
         * synchronous). GL_PACK_ALIGNMENT must be 1 for non-4-multiple widths. */
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.getColorAttachment());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        encodeAndWrite(buffer, width, height, destination, onWritten);
    }

    /**
     * World variant convenience: capture into a freshly-named file under
     * {@link #getScreenshotsDir()}, reporting it to the notifier the request
     * carried (P298).
     */
    public static void captureWorldFrame()
    {
        captureWorldFrame(worldFile(getScreenshotsDir()), WORLD_NOTIFIER.getAndSet(null));
    }

    /**
     * Model-editor variant: draw {@code draw} over P46's transparent offscreen
     * target and write {@code screenshots/blockbuster/<model>_<timestamp>.png}.
     * The editor supplies its viewport size and the currently-edited model name.
     */
    public static void captureModel(int width, int height, Runnable draw, String modelName)
    {
        File destination = modelFile(getScreenshotsDir(), modelName);

        mchorse.mclib.client.ScreenshotCapture.capture(width, height, draw, destination);
    }
}
