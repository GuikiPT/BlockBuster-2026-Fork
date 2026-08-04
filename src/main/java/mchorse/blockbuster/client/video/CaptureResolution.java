package mchorse.blockbuster.client.video;

/**
 * S22 <b>P270</b> — the pure half of custom-resolution capture (S18 P200): turn
 * {@code video.width}/{@code video.height} plus the window's framebuffer size
 * into the resolution a recording is actually made at, and say whether the
 * custom-resolution render path has to engage for it.
 *
 * <h2>The 1.12.2 contract this reproduces</h2>
 *
 * <p>1.12.2 delegated capture to <b>Minema</b> (3.7.1 in the reference instance),
 * so there is no legacy source in the trees — but the shipped mod is the spec,
 * and its arithmetic is recoverable. {@code MinemaConfig.getFrameWidth()} is:</p>
 *
 * <pre>
 * int w = frameWidth.get();
 * if (w == 0) w = Display.getWidth();          // "Set to 0 to use the current window width"
 * if (useVideoEncoder) w = snapResolution.snap(w);
 * return w;
 *
 * SnapResolution.snap(v) = mod == 0 ? v : v - (v % mod);   // snap DOWN to a multiple
 * </pre>
 *
 * <p>and the display-size override engaged exactly when
 * {@code getFrameWidth() != Display.getWidth() || getFrameHeight() != Display.getHeight()}
 * ({@code MinemaConfig.useFrameSize()}). Three things follow, and all three are
 * reproduced here:</p>
 *
 * <ol>
 * <li><b>0 means "the window"</b> — the port's {@code video.width}/
 * {@code video.height} already used that convention (P250); it is now recorded
 * as legacy-verified rather than invented.</li>
 * <li><b>The size is snapped <i>down</i> to a modulus before anything else sees
 * it.</b> Minema offered mod2/4/8/16 (the reference config ships {@code mod16})
 * and its own comment says <i>"FFMpeg only needs mod2"</i>. The port has no
 * snap config key and takes the mod2 floor, which is exactly
 * {@link VideoParams#clampEven(int)} — so {@code clampEven} is not a port
 * invention either, it is {@code SnapResolution.MOD2.snap} with a floor of 2.</li>
 * <li><b>The snap decides whether the override engages</b>, not the raw config
 * value. That is why an odd-width window engages the custom path here even with
 * {@code width = height = 0}: the recording is 1920 wide, the window is 1921, and
 * without the swap the readback would find a 1921-wide texture where the encoder
 * was promised 1920 and emit <i>blank frames</i>
 * ({@link FramebufferFrameSource}). Legacy had the identical condition.</li>
 * </ol>
 *
 * <h2>Blockers</h2>
 *
 * <p>{@code blocker} is a human-readable reason the custom-resolution path must
 * not engage on this client (an Iris shader pack, Fabulous graphics — see
 * {@code CustomResolutionCapture.blocker()}). When one is present the recording
 * falls back to the <b>window</b> size with a logged warning, which is the same
 * honest clamp P250 shipped, just no longer unconditional. Legacy had this
 * concept too: Minema's {@code aaFastRenderFix} exists because "optifine's
 * antialiasing or fast render together with a custom resolution" produced broken
 * recordings, and its workaround was to resize the real OS window instead.</p>
 *
 * <p>Pure — no GL, no Minecraft, headlessly testable.</p>
 */
public final class CaptureResolution
{
    /** The resolved recording size plus why it is what it is. */
    public static final class Decision
    {
        private final int width;
        private final int height;
        private final boolean custom;
        private final String blocker;
        private final int requestedWidth;
        private final int requestedHeight;

        Decision(int width, int height, boolean custom, String blocker, int requestedWidth, int requestedHeight)
        {
            this.width = width;
            this.height = height;
            this.custom = custom;
            this.blocker = blocker;
            this.requestedWidth = requestedWidth;
            this.requestedHeight = requestedHeight;
        }

        /** The width the recording is made at. */
        public int width()
        {
            return this.width;
        }

        /** The height the recording is made at. */
        public int height()
        {
            return this.height;
        }

        /**
         * Whether the world has to be rendered into a capture framebuffer of its
         * own (and the window lied to about its size) to produce this. False ⇒
         * the recording is the window's own framebuffer, i.e. exactly the path
         * that shipped before P270.
         */
        public boolean custom()
        {
            return this.custom;
        }

        /** Why the requested size was refused, or {@code null} when it was not. */
        public String blocker()
        {
            return this.blocker;
        }

        /** The size that <i>was</i> asked for — differs from {@link #width()} only when blocked. */
        public int requestedWidth()
        {
            return this.requestedWidth;
        }

        /** @see #requestedWidth() */
        public int requestedHeight()
        {
            return this.requestedHeight;
        }

        /** True when a request was downgraded to the window size. */
        public boolean clamped()
        {
            return this.blocker != null;
        }
    }

    private CaptureResolution()
    {}

    /**
     * Resolve the recording size.
     *
     * @param configWidth  {@code video.width} ({@code 0} ⇒ the window)
     * @param configHeight {@code video.height} ({@code 0} ⇒ the window)
     * @param windowWidth  the window framebuffer's width in pixels
     * @param windowHeight the window framebuffer's height in pixels
     * @param blocker      a reason the custom path cannot engage, or {@code null}
     */
    public static Decision resolve(int configWidth, int configHeight, int windowWidth, int windowHeight, String blocker)
    {
        /* Legacy MinemaConfig.getFrameWidth(): 0 ⇒ window, then snap. */
        int width = VideoParams.clampEven(configWidth > 0 ? configWidth : windowWidth);
        int height = VideoParams.clampEven(configHeight > 0 ? configHeight : windowHeight);

        /* Legacy MinemaConfig.useFrameSize(): compare the SNAPPED size, not the
         * configured one — an odd window is a custom resolution. */
        boolean custom = width != windowWidth || height != windowHeight;

        if (custom && blocker != null)
        {
            return new Decision(windowWidth, windowHeight, false, blocker, width, height);
        }

        return new Decision(width, height, custom, null, width, height);
    }

    /** The unconditional fallback: record whatever the window is, no swap. */
    public static Decision window(int windowWidth, int windowHeight, String reason)
    {
        return new Decision(windowWidth, windowHeight, false, reason, windowWidth, windowHeight);
    }
}
