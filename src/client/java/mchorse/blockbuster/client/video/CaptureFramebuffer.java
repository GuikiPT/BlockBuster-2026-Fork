package mchorse.blockbuster.client.video;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.render.WorldRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Real GL/Minecraft adapter for custom-resolution capture (P200): implements
 * {@link FramebufferGl} against {@link MinecraftClient#framebuffer} (reassigned
 * through the access widener) and the six {@link WorldRenderer} sub-buffers. The
 * swap/restore/resize <i>bookkeeping</i> lives in the unit-tested
 * {@link CaptureFramebufferState}; this class is the thin, GL-touching seam.
 *
 * <p><b>S22 P270 — installed.</b> This was {@code SEAM(P200-window)}: built by
 * P234, left uninstalled because a framebuffer swap without the window-size
 * override renders the world at the window's size, aspect ratio and GUI scale
 * into a differently sized target. P270 lands the override
 * ({@code WindowMixin}), the swap and the six sub-buffer resizes as one unit —
 * see {@link CustomResolutionCapture} for the technique decision and its blast
 * radius, and {@link CaptureResolution} for the 1.12.2/Minema contract the sizes
 * come from.</p>
 *
 * <p>The capture target is a {@link WindowFramebuffer}, which negotiates a size
 * the driver actually supports — so {@link #prepare} verifies it came back at
 * the requested size and refuses otherwise, rather than promising the encoder a
 * frame size the readback would never match (Minema documented the same limit:
 * non-zero frame sizes are "bound to the maximum texture resolution of your
 * GPU").</p>
 */
public class CaptureFramebuffer implements FramebufferGl, CustomResolutionCapture.Swap
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    private final MinecraftClient mc;
    private WindowFramebuffer captureFramebuffer;
    private final CaptureFramebufferState state;

    public CaptureFramebuffer(MinecraftClient mc)
    {
        this.mc = mc;
        this.state = new CaptureFramebufferState(this);
    }

    /* CustomResolutionCapture.Swap */

    /**
     * Allocate the capture framebuffer and confirm the driver gave us exactly the
     * size asked for.
     */
    @Override
    public boolean prepare(int width, int height)
    {
        if (this.captureFramebuffer != null
            && (this.captureFramebuffer.textureWidth != width || this.captureFramebuffer.textureHeight != height))
        {
            this.dispose();
        }

        if (this.captureFramebuffer == null)
        {
            this.captureFramebuffer = new WindowFramebuffer(width, height);
        }

        if (this.captureFramebuffer.textureWidth != width || this.captureFramebuffer.textureHeight != height)
        {
            LOGGER.warn("The driver could not provide a {}x{} capture framebuffer (got {}x{}); recording at the window size instead",
                width, height, this.captureFramebuffer.textureWidth, this.captureFramebuffer.textureHeight);

            this.dispose();

            return false;
        }

        return true;
    }

    /**
     * Create (or resize) the capture framebuffer and swap it in at
     * {@code width x height}.
     */
    @Override
    public void begin(int width, int height)
    {
        if (this.captureFramebuffer == null)
        {
            this.captureFramebuffer = new WindowFramebuffer(width, height);
        }

        this.state.begin(this.captureFramebuffer, width, height);
    }

    /** Restore the client framebuffer (call from the renderWorld RETURN seam). */
    @Override
    public void end()
    {
        this.state.restore();
    }

    /** End of recording: sub-buffers back to the window size, capture target freed. */
    @Override
    public void release()
    {
        this.state.release();
        this.dispose();
    }

    public Framebuffer captureFramebuffer()
    {
        return this.captureFramebuffer;
    }

    public void dispose()
    {
        if (this.captureFramebuffer != null)
        {
            this.captureFramebuffer.delete();
            this.captureFramebuffer = null;
        }
    }

    /* FramebufferGl — GL-touching seam. */

    @Override
    public Object currentTarget()
    {
        /* Access-widened MinecraftClient.framebuffer (see blockbuster.accesswidener). */
        return this.mc.framebuffer;
    }

    @Override
    public void setTarget(Object framebuffer)
    {
        this.mc.framebuffer = (Framebuffer) framebuffer;
    }

    @Override
    public List<Object> subFramebuffers()
    {
        WorldRenderer wr = this.mc.worldRenderer;
        List<Object> list = new ArrayList<>(6);

        list.add(wr.getEntityOutlinesFramebuffer());
        list.add(wr.getTranslucentFramebuffer());
        list.add(wr.getEntityFramebuffer());
        list.add(wr.getParticlesFramebuffer());
        list.add(wr.getWeatherFramebuffer());
        list.add(wr.getCloudsFramebuffer());
        list.removeIf(fb -> fb == null);

        return list;
    }

    @Override
    public void resize(Object framebuffer, int width, int height)
    {
        ((Framebuffer) framebuffer).resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
    }

    @Override
    public int width(Object framebuffer)
    {
        return ((Framebuffer) framebuffer).textureWidth;
    }

    @Override
    public int height(Object framebuffer)
    {
        return ((Framebuffer) framebuffer).textureHeight;
    }

    @Override
    public void beginWrite(Object framebuffer)
    {
        ((Framebuffer) framebuffer).beginWrite(true);
    }

    @Override
    public void draw(Object framebuffer, int width, int height)
    {
        ((Framebuffer) framebuffer).draw(width, height);
    }
}
