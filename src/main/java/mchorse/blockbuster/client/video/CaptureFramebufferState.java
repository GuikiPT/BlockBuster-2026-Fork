package mchorse.blockbuster.client.video;

/**
 * Swap/restore + dependent-framebuffer-resize bookkeeping for custom-resolution
 * capture (P200, engaged by S22 P270), extracted as a pure state machine so the
 * logic is unit testable with GL mocked ({@link FramebufferGl}).
 *
 * <h2>Lifecycle — three calls, not two</h2>
 *
 * <p>{@link #begin} and {@link #restore} run <b>once per rendered world frame</b>
 * (from the {@code GameRenderer.renderWorld} HEAD/RETURN seams), which is what
 * makes the third call necessary:</p>
 *
 * <ul>
 * <li>{@link #begin} saves the client's current framebuffer, swaps the capture
 * framebuffer in, sizes it and the six {@code WorldRenderer} sub-buffers to the
 * capture resolution, and binds it for writing.</li>
 * <li>{@link #restore} puts the saved framebuffer back, rebinds it, and blits
 * the capture buffer into it as the on-screen preview. It deliberately does
 * <b>not</b> resize the sub-buffers back: they are only ever read by the world
 * render, every world render during a recording goes through {@link #begin},
 * and resizing them twice a frame would reallocate six textures per frame.</li>
 * <li>{@link #release} ends the recording: restore if still swapped, then put
 * the sub-buffers back at the <i>current</i> client framebuffer's size (read
 * fresh, so a window resize mid-recording does not restore a stale size).</li>
 * </ul>
 *
 * <p>Every resize goes through {@link #resizeIfNeeded}, so the per-frame
 * {@code begin} costs nothing after the first frame.</p>
 *
 * <p>Callers wrap the render in {@code begin() … restore()} with the restore on
 * the RETURN seam, and {@code begin} self-heals a leftover swap (a frame that
 * threw never reached its RETURN): {@link #restore} is idempotent, which is what
 * makes that safe. A stale target would otherwise leave the client rendering
 * into the capture buffer forever.</p>
 */
public class CaptureFramebufferState
{
    private final FramebufferGl gl;

    private boolean swapped;
    private boolean subsResized;
    private Object saved;
    private int savedWidth;
    private int savedHeight;
    private Object capture;
    private int captureWidth;
    private int captureHeight;

    public CaptureFramebufferState(FramebufferGl gl)
    {
        this.gl = gl;
    }

    public boolean isSwapped()
    {
        return this.swapped;
    }

    /** Whether the world sub-buffers are currently sized for capture. */
    public boolean isSubFramebuffersResized()
    {
        return this.subsResized;
    }

    /**
     * Swap the capture framebuffer in at {@code width x height}. No-op if already
     * swapped.
     */
    public void begin(Object captureFramebuffer, int width, int height)
    {
        if (this.swapped)
        {
            return;
        }

        this.saved = this.gl.currentTarget();
        this.savedWidth = this.gl.width(this.saved);
        this.savedHeight = this.gl.height(this.saved);
        this.capture = captureFramebuffer;
        this.captureWidth = width;
        this.captureHeight = height;

        this.gl.setTarget(this.capture);
        this.resizeIfNeeded(this.capture, width, height);

        /* All six WorldRenderer sub-buffers move in lockstep or outlines,
         * translucency, particles, weather and clouds render at the wrong size
         * (and the fabulous depth copies blit between mismatched rects). */
        for (Object sub : this.gl.subFramebuffers())
        {
            this.resizeIfNeeded(sub, width, height);
        }

        this.subsResized = true;

        this.gl.beginWrite(this.capture);
        this.swapped = true;
    }

    /**
     * Restore the saved framebuffer, rebind it, and blit the capture buffer into
     * it as the on-screen preview. Idempotent.
     *
     * <p>The preview blit is at the <b>destination</b> size (the window), not the
     * capture size: {@code Framebuffer.draw(w, h)} sets the viewport it draws its
     * colour texture into, so passing the capture size would scale a 4K capture
     * into a 4K viewport of a 1080p window and show the user a corner of the
     * frame.</p>
     */
    public void restore()
    {
        if (!this.swapped)
        {
            return;
        }

        this.swapped = false;

        this.gl.setTarget(this.saved);
        this.gl.beginWrite(this.saved);
        this.gl.draw(this.capture, this.savedWidth, this.savedHeight);
    }

    /**
     * End of recording: restore, then put the world sub-buffers back at the
     * client framebuffer's current size. Idempotent.
     */
    public void release()
    {
        this.restore();

        if (!this.subsResized)
        {
            return;
        }

        this.subsResized = false;

        Object target = this.gl.currentTarget();
        int width = this.gl.width(target);
        int height = this.gl.height(target);

        for (Object sub : this.gl.subFramebuffers())
        {
            this.resizeIfNeeded(sub, width, height);
        }
    }

    /** Resize only when the target is not already that size (per-frame safety). */
    private void resizeIfNeeded(Object framebuffer, int width, int height)
    {
        if (this.gl.width(framebuffer) == width && this.gl.height(framebuffer) == height)
        {
            return;
        }

        this.gl.resize(framebuffer, width, height);
    }
}
