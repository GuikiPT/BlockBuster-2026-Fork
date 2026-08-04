package mchorse.mclib.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Transparent GUI/editor screenshot support (roadmap S3 P46 — the foundation
 * for S18's alpha world-captures).
 *
 * <p>1.12.2 mclib had no equivalent class; the behavior bar is the roadmap's
 * feature intent: render into an offscreen target <b>with alpha</b>, dump an
 * RGBA PNG (F2-style trigger in editors). Technique validated against BBS's
 * {@code ScreenshotRecorder} (readback + row flip + async file write).</p>
 *
 * <p>Design:</p>
 * <ul>
 * <li>Vanilla {@code ScreenshotRecorder.takeScreenshot} force-opaques alpha
 * ({@code NativeImage.loadFromTextureImage(0, true)}) — unusable here. The
 * capture path does its own {@code glReadPixels} readback and the pure
 * RGBA→ARGB/row-flip/PNG-encode helpers below are the production encode
 * path (headless-testable, no natives).</li>
 * <li>GUI shaders blend without writing destination alpha —
 * {@link #beginCapture} installs
 * {@code blendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE,
 * ONE_MINUS_SRC_ALPHA)} so destination alpha accumulates;
 * {@link #saveCapture} restores the default blend func.</li>
 * <li>GL readback is bottom-up — {@link #rgbaToArgbFlipped} flips rows.</li>
 * <li>Wiring: the F2 trigger on editor panels belongs to the dashboard
 * (P44/S18) — before capturing, the trigger must suppress input overlays
 * for the frame (P44.2 {@code InputRenderer.disable()}) and replay the GUI
 * draw between {@link #beginCapture} and {@link #saveCapture} (see
 * {@link #capture}).</li>
 * </ul>
 */
public class ScreenshotCapture
{
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    /* GL entry points (render thread only; classload headlessly) */

    /**
     * Create and bind an offscreen render target with an alpha channel and a
     * fully transparent clear color, ready for a GUI replay draw.
     */
    public static Framebuffer beginCapture(int width, int height)
    {
        SimpleFramebuffer framebuffer = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);

        framebuffer.setClearColor(0F, 0F, 0F, 0F);
        framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
        framebuffer.beginWrite(true);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        return framebuffer;
    }

    /**
     * Read the capture target back (alpha preserved), rebind the main
     * framebuffer, delete the capture target and write the PNG off-thread.
     */
    public static void saveCapture(Framebuffer framebuffer, File destination)
    {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);

        GlStateManager._readPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        framebuffer.endWrite();
        framebuffer.delete();

        RenderSystem.defaultBlendFunc();

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.getFramebuffer() != null)
        {
            mc.getFramebuffer().beginWrite(true);
        }

        final int[] pixels = rgbaToArgbFlipped(buffer, width, height);

        Util.getIoWorkerExecutor().execute(() ->
        {
            try
            {
                writePng(pixels, width, height, destination);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        });
    }

    /**
     * Convenience wrapper for S18 reuse: bind an alpha target, run the given
     * draw replay, dump the PNG.
     */
    public static void capture(int width, int height, Runnable draw, File destination)
    {
        /* P44.2: keep the tutorials overlay (cursor / mouse / keystrokes) out
         * of the captured frame — legacy's frame-capture callers did the same */
        InputRenderer.disable();

        Framebuffer framebuffer = beginCapture(width, height);

        try
        {
            draw.run();
        }
        finally
        {
            saveCapture(framebuffer, destination);
        }
    }

    /* Pure helpers (production encode path — headless-tested) */

    /**
     * Convert a bottom-up GL RGBA readback into a top-down ARGB pixel array
     * (row flip included).
     */
    public static int[] rgbaToArgbFlipped(ByteBuffer rgba, int width, int height)
    {
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++)
        {
            int srcRow = (height - 1 - y) * width * 4;

            for (int x = 0; x < width; x++)
            {
                int i = srcRow + x * 4;
                int r = rgba.get(i) & 0xff;
                int g = rgba.get(i + 1) & 0xff;
                int b = rgba.get(i + 2) & 0xff;
                int a = rgba.get(i + 3) & 0xff;

                pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        return pixels;
    }

    /**
     * Encode a top-down ARGB pixel array as an RGBA PNG (alpha preserved,
     * incl. 0 and partial alpha).
     */
    public static void writePng(int[] argbTopDown, int width, int height, OutputStream out) throws IOException
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        image.setRGB(0, 0, width, height, argbTopDown, 0, width);
        ImageIO.write(image, "png", out);
    }

    public static void writePng(int[] argbTopDown, int width, int height, File file) throws IOException
    {
        File parent = file.getParentFile();

        if (parent != null)
        {
            parent.mkdirs();
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        image.setRGB(0, 0, width, height, argbTopDown, 0, width);
        ImageIO.write(image, "png", file);
    }

    /**
     * Vanilla screenshots-folder naming convention:
     * {@code yyyy-MM-dd_HH.mm.ss.png}, deduplicated with {@code _N}
     * suffixes (same scheme as {@code ScreenshotRecorder}).
     */
    public static File getScreenshotFile(File screenshotsDir)
    {
        screenshotsDir.mkdirs();

        String base = DATE_FORMAT.format(new Date());
        int i = 1;

        while (true)
        {
            File file = new File(screenshotsDir, base + (i == 1 ? "" : "_" + i) + ".png");

            if (!file.exists())
            {
                return file;
            }

            i++;
        }
    }

    /**
     * The vanilla screenshots directory ({@code <run dir>/screenshots}) —
     * where editor captures land, like regular F2 screenshots.
     */
    public static File getScreenshotsDir()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        File run = mc != null ? mc.runDirectory : new File(".");

        return new File(run, "screenshots");
    }
}
