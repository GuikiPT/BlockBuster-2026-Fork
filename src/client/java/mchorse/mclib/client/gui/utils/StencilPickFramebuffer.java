package mchorse.mclib.client.gui.utils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Offscreen stencil-capable framebuffer for GUI limb picking (roadmap P84/P158).
 *
 * <p>1.12.2 Forge shipped a main framebuffer with a packed depth/stencil
 * attachment (Forge's {@code enableStencilBits}), so McLib's
 * {@code GuiModelRenderer.tryPicking} could simply
 * {@code glClear(GL_STENCIL_BUFFER_BIT)} + {@code glReadPixels(GL_STENCIL_INDEX)}
 * against whatever was bound. Vanilla 1.20.4 / Fabric has <b>no</b> stencil
 * attachment on the main framebuffer, so that readback always returns 0 and
 * Ctrl+click limb picking never fires.</p>
 *
 * <p>This class supplies the missing capability: a lazily created FBO the size
 * of the window framebuffer with a colour renderbuffer plus a packed
 * {@code GL_DEPTH24_STENCIL8} renderbuffer. The pick pass binds it, clears it,
 * draws the stencil-tagged geometry, reads the pixel back and restores the
 * previous binding. It is resized on demand and released with
 * {@link #delete()}.</p>
 *
 * <p>Everything here is best-effort: without a GL context (headless tests) or
 * on FBO-incomplete hardware every method is inert and {@link #readStencil}
 * reports {@code -1} = "no pick", exactly like a miss.</p>
 */
public class StencilPickFramebuffer
{
    private int fbo = -1;
    private int colorBuffer = -1;
    private int depthStencilBuffer = -1;

    private int width;
    private int height;

    /** Whether the framebuffer is allocated and complete at the given size. */
    public boolean isReady(int width, int height)
    {
        return this.fbo > 0 && this.width == width && this.height == height;
    }

    /**
     * Allocate (or reallocate) the framebuffer at the given size. Returns
     * whether it is usable afterwards.
     */
    public boolean setup(int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return false;
        }

        if (this.isReady(width, height))
        {
            return true;
        }

        this.delete();

        try
        {
            this.fbo = GL30.glGenFramebuffers();
            this.colorBuffer = GL30.glGenRenderbuffers();
            this.depthStencilBuffer = GL30.glGenRenderbuffers();

            int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);

            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.colorBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, this.colorBuffer);

            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.depthStencilBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, this.depthStencilBuffer);

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);

            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                this.delete();

                return false;
            }

            this.width = width;
            this.height = height;

            return true;
        }
        catch (Throwable t)
        {
            this.delete();

            return false;
        }
    }

    /**
     * Bind this framebuffer and clear its colour/depth/stencil. Returns the
     * previously bound framebuffer so the caller can restore it, or {@code -1}
     * when the bind failed (nothing was changed).
     */
    public int bindAndClear()
    {
        if (this.fbo <= 0)
        {
            return -1;
        }

        try
        {
            int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
            GL11.glClearStencil(0);
            GL11.glClearDepth(1.0D);
            GL11.glClearColor(0F, 0F, 0F, 0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

            return previous;
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    public void unbind(int previous)
    {
        if (previous < 0)
        {
            return;
        }

        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
        catch (Throwable t)
        {}
    }

    /**
     * Read a single stencil pixel as an <b>unsigned</b> value (McLib's original
     * read used a signed {@code ByteBuffer.get()}, so limbs 128+ came back
     * negative and their picks were silently dropped). Returns {@code -1} when
     * the read is impossible.
     */
    public int readStencil(int x, int y)
    {
        try
        {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, buffer);
            buffer.rewind();

            return buffer.get() & 0xFF;
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    public void delete()
    {
        try
        {
            if (this.fbo > 0)
            {
                GL30.glDeleteFramebuffers(this.fbo);
            }

            if (this.colorBuffer > 0)
            {
                GL30.glDeleteRenderbuffers(this.colorBuffer);
            }

            if (this.depthStencilBuffer > 0)
            {
                GL30.glDeleteRenderbuffers(this.depthStencilBuffer);
            }
        }
        catch (Throwable t)
        {}

        this.fbo = -1;
        this.colorBuffer = -1;
        this.depthStencilBuffer = -1;
        this.width = 0;
        this.height = 0;
    }
}
