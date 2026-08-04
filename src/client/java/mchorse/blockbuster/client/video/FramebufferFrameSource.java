package mchorse.blockbuster.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * The <b>production</b> {@link FrameSource} (S22 P234): a GL readback of a
 * framebuffer's colour attachment into the recorder's staging buffer.
 *
 * <p>Until P234 the {@code FrameSource} seam had only the P206 test generator as
 * an implementor and {@link VideoRecorder} kept its own inlined PBO ring, so
 * nothing in the running client ever produced a frame. This class is that
 * missing half: same technique the P204 still-screenshot path already uses
 * ({@code glBindTexture} + {@code glGetTexImage} on
 * {@link Framebuffer#getColorAttachment()}), in {@code GL_BGR} / {@code GL_BGRA}
 * so the bytes are exactly what ffmpeg's {@code -pix_fmt bgr24}/{@code bgra}
 * raw-video input expects and what {@link PngSequenceSink} de-swizzles.</p>
 *
 * <p><b>Render thread only.</b> The recorder calls this from Fabric's
 * {@code WorldRenderEvents.LAST} — after the world draws and before the HUD, so
 * the HUD/GUI is never in the frame (the panel additionally hides the camera
 * editor root while recording, P202).</p>
 *
 * <h2>Bottom-up rows</h2>
 * <p>GL hands back rows bottom-up. Nothing flips here: the ffmpeg path carries a
 * {@code vflip} filter ({@link VideoParams#filters()}) and the PNG sink flips
 * while swizzling. Flipping here would double-flip both.</p>
 *
 * <h2>Resolution changes mid-recording</h2>
 * <p>The frame size is fixed at {@code startRecording} time (the encoder was
 * told those dimensions and cannot be renegotiated). If the window is resized
 * mid-recording the framebuffer texture no longer matches, and
 * {@code glGetTexImage} would write the <i>texture's</i> byte count into a
 * differently sized buffer — an overflow. So a mismatched frame is skipped and
 * the cleared (black) staging buffer is emitted instead, with a single logged
 * warning. Resizing mid-capture is a user error; a black frame is the
 * total-reader answer, a crash is not.</p>
 */
public class FramebufferFrameSource implements FrameSource
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    private final Supplier<Framebuffer> framebuffer;
    private final VideoFormat format;
    private final int width;
    private final int height;

    private boolean warnedSize;
    private boolean warnedMissing;

    public FramebufferFrameSource(Supplier<Framebuffer> framebuffer, VideoFormat format, int width, int height)
    {
        this.framebuffer = framebuffer;
        this.format = format;
        this.width = width;
        this.height = height;
    }

    /**
     * Read back the client's main framebuffer at the recording's resolution.
     *
     * <p>The framebuffer is resolved per frame rather than captured once: the
     * client replaces the object on a window resize, and holding a stale
     * reference would read a deleted texture.</p>
     */
    public static FramebufferFrameSource mainFramebuffer(VideoParams params)
    {
        return new FramebufferFrameSource(
            () ->
            {
                MinecraftClient mc = MinecraftClient.getInstance();

                return mc == null ? null : mc.getFramebuffer();
            },
            params.format(), params.width(), params.height());
    }

    /** The GL pixel format the readback requests ({@code GL_BGR} / {@code GL_BGRA}). */
    public int glFormat()
    {
        return this.format.hasAlpha() ? GL12.GL_BGRA : GL12.GL_BGR;
    }

    @Override
    public void readFrame(ByteBuffer dst)
    {
        Framebuffer target = this.framebuffer.get();

        if (target == null)
        {
            if (!this.warnedMissing)
            {
                this.warnedMissing = true;

                LOGGER.warn("No framebuffer to capture from; emitting blank frames");
            }

            dst.position(dst.limit());

            return;
        }

        if (target.textureWidth != this.width || target.textureHeight != this.height)
        {
            if (!this.warnedSize)
            {
                this.warnedSize = true;

                LOGGER.warn("Framebuffer is {}x{} but the recording is {}x{} (window resized mid-recording?); emitting blank frames",
                    target.textureWidth, target.textureHeight, this.width, this.height);
            }

            dst.position(dst.limit());

            return;
        }

        /* GL_PACK_ALIGNMENT must be 1: BGR rows of a non-multiple-of-4 width are
         * otherwise padded and every row after the first is skewed. */
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorAttachment());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, this.glFormat(), GL11.GL_UNSIGNED_BYTE, dst);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        /* glGetTexImage writes through the buffer without moving its position;
         * the FrameSource contract is that the whole buffer was consumed. */
        dst.position(dst.limit());
    }
}
