package mchorse.blockbuster.client.video;

/**
 * P199 — Fixed-timestep playback clock (GL-free timing core).
 *
 * <p>This is a port addition: 1.12.2 delegated capture timing to the external
 * Minema mod, so there is no legacy class to mirror name-for-name. What we
 * <b>do</b> preserve is the behavioral contract legacy code depends on — the
 * three notions Aperture's {@code CameraExporter} counts against
 * (output frame index, held frames, motion-blur subframe index) — so the
 * P202.1 exporter port can gate frames byte-for-byte the way it did against
 * Minema.</p>
 *
 * <p>All logic lives here, free of OpenGL and Minecraft, so it can be driven
 * headlessly by JUnit. The {@code RenderTickCounterMixin} is a thin adapter
 * that calls {@link #tick()} once per {@code beginRenderTick} and copies the
 * result onto the shadowed vanilla fields.</p>
 *
 * <h2>Model</h2>
 * <ul>
 * <li><b>fps</b> — the final output video frame rate (e.g. 30).</li>
 * <li><b>motionBlurLevel</b> — number of doublings; the renderer produces
 * {@code 2^level} sub-frames per output frame, later averaged down to one by
 * ffmpeg (see P201). {@code motionBlurFrames = 1 << level}.</li>
 * <li><b>captureFps</b> = {@code fps * motionBlurFrames} — the rate at which
 * real (clock-advancing) frames are rendered.</li>
 * <li><b>heldFrames</b> — each real frame is preceded by {@code heldFrames - 1}
 * <i>held repeats</i> that render the same game time again without advancing
 * the clock (Minema's "held frames"; lets slow/async effects settle before the
 * real captured frame). {@code heldFrames == 1} disables holding.</li>
 * </ul>
 *
 * <p>Each real frame advances game time by exactly {@code 20 / captureFps}
 * ticks. To keep that exact (and to make the unit invariants hold to the tick),
 * accumulation is done in integer arithmetic: a {@code numerator} accumulates
 * {@code +20} per real frame and whole ticks are {@code numerator / captureFps}
 * with the remainder carried, so the fractional {@link Decision#tickDelta} is
 * always {@code remainder / captureFps} in {@code [0, 1)} and the whole ticks
 * over any full second of video sum to exactly {@code 20}.</p>
 *
 * <h2>Held-frame convention</h2>
 * <p>Following the <b>legacy exporter</b> accounting (not BBS's mixin, which
 * captures on the first held repeat): the real / captured frame is the
 * <b>last</b> repeat of each held group. The {@link #getHeldFrame() heldFrame}
 * counter cycles {@code 1..heldFrames}; a frame is real exactly when
 * {@code heldFrame >= heldFrames}, mirroring Aperture's
 * {@code CameraExporter.skipFrame()} which skips while
 * {@code heldframes < config.heldFrames}. The clock output is identical either
 * way (held repeats contribute 0 elapsed ticks regardless of position); the
 * choice only fixes <i>which</i> repeat carries the capture, and we pick the
 * one the exporter expects.</p>
 *
 * <h2>Authorized vs. consumed server ticks (S22 P295)</h2>
 * <p>{@link #tick()} is only half of a fixed-timestep capture clock. It tells
 * the <b>client</b> how much game time this frame is worth; nothing in it makes
 * the <b>integrated server</b> agree. Until P295 the server kept ticking at
 * wall-clock 20 TPS while the render clock advanced game time at
 * {@code renderFps / captureFps} of real speed, so the server world — and with
 * it scene playback, actors and every {@code END_SERVER_TICK} driver — ran at a
 * completely different rate from the frames being encoded. There was no
 * slow-motion and no lockstep, only a client that got snapped forward by
 * {@code PacketSyncTick}.</p>
 *
 * <p>So the clock keeps <b>two</b> totals, and their difference is a debt:</p>
 * <ul>
 * <li>{@link #getServerTicks()} — ticks the clock has <b>authorized</b>: the
 * running sum of {@link Decision#elapsedTicks}, written by the render thread.
 * Held repeats add 0 (a repeat re-renders the same game time, so it must buy no
 * world time); a motion-blur sub-frame adds its own
 * {@code 20 / (fps << level)} share, since sub-frames are real,
 * clock-advancing frames.</li>
 * <li>{@link #getConsumedServerTicks()} — ticks the integrated server has
 * actually run, written by the server thread from
 * {@link #claimServerTicks()}.</li>
 * </ul>
 *
 * <p>{@code IntegratedServerCaptureMixin} runs exactly
 * {@link #claimServerTicks()} vanilla server ticks per {@code IntegratedServer
 * .tick}, which keeps the debt at zero over time and never lets the world run
 * ahead of the footage.</p>
 *
 * <p><b>Threading.</b> This is the one part of the clock that is <i>not</i>
 * single-threaded: the render thread authorizes, the server thread consumes.
 * Each of the two counters has exactly one writing thread and both are
 * {@code volatile}, so no update can be lost and neither thread can read a
 * stale total indefinitely. No lock is needed and none is taken — the server
 * tick must never block on the render thread.</p>
 */
public final class CaptureTiming
{
    /** Vanilla ticks per second — one real second of video advances by this. */
    public static final int TICKS_PER_SECOND = 20;

    private final int fps;
    private final int heldFrames;
    private final int motionBlurLevel;

    /** {@code 1 << motionBlurLevel} — rendered sub-frames per output frame. */
    private final int motionBlurFrames;
    /** {@code fps * motionBlurFrames} — real (clock-advancing) frames/second. */
    private final int captureFps;
    /** Constant {@code 20f / captureFps}, for the vanilla lastFrameDuration field. */
    private final float frameDuration;

    /** Legacy held counter, cycling {@code 1..heldFrames}. Starts at 0. */
    private int heldFrame;
    /** Number of real (clock-advancing) frames emitted so far. */
    private long realCount;
    /** Integer tick accumulator in units of {@code 1 / captureFps} ticks. */
    private long numerator;
    /**
     * Total whole ticks the clock has <b>authorized</b> since construction.
     *
     * <p>Written only by the render thread (from {@link #tick()}), read by the
     * integrated-server thread through {@link #claimServerTicks()} — hence
     * {@code volatile}. See the class javadoc's threading note.</p>
     */
    private volatile long serverTicks;
    /**
     * Total whole ticks the integrated server has actually <b>consumed</b>
     * (P295). Written only by the server thread, from
     * {@link #claimServerTicks()}.
     */
    private volatile long consumedServerTicks;

    public CaptureTiming(int fps, int heldFrames, int motionBlurLevel)
    {
        if (fps <= 0)
        {
            throw new IllegalArgumentException("fps must be positive, got " + fps);
        }

        this.fps = fps;
        this.heldFrames = Math.max(1, heldFrames);
        this.motionBlurLevel = Math.max(0, motionBlurLevel);

        this.motionBlurFrames = 1 << this.motionBlurLevel;
        this.captureFps = fps * this.motionBlurFrames;
        this.frameDuration = (float) TICKS_PER_SECOND / this.captureFps;
    }

    public int getFps()
    {
        return this.fps;
    }

    public int getHeldFrames()
    {
        return this.heldFrames;
    }

    public int getMotionBlurLevel()
    {
        return this.motionBlurLevel;
    }

    /** Rendered sub-frames per output frame ({@code 2^motionBlurLevel}). */
    public int getMotionBlurFrames()
    {
        return this.motionBlurFrames;
    }

    /** Real, clock-advancing frames rendered per second ({@code fps << level}). */
    public int getCaptureFps()
    {
        return this.captureFps;
    }

    /** {@code 20f / captureFps} — feeds the vanilla lastFrameDuration field. */
    public float getFrameDuration()
    {
        return this.frameDuration;
    }

    /** Legacy held counter (0 before the first tick, then cycles 1..heldFrames). */
    public int getHeldFrame()
    {
        return this.heldFrame;
    }

    /** Real frames emitted so far (Aperture's {@code CameraExporter.frame}). */
    public long getRealCount()
    {
        return this.realCount;
    }

    /** Total whole ticks advanced since start (the recorder's serverTicks). */
    public long getServerTicks()
    {
        return this.serverTicks;
    }

    /**
     * Whole ticks the integrated server has already been allowed to run
     * (P295). Never greater than {@link #getServerTicks()}.
     */
    public long getConsumedServerTicks()
    {
        return this.consumedServerTicks;
    }

    /**
     * Ticks the clock has authorized but the integrated server has not run yet
     * (P295) — {@code getServerTicks() - getConsumedServerTicks()}, floored at 0.
     *
     * <p>Diagnostic / test accessor: it reads the two counters separately, so
     * from the server thread the answer can be one render frame stale. That is
     * harmless because it can only ever be an <i>under</i>-count that the next
     * pass picks up; {@link #claimServerTicks()} is what the mixin uses.</p>
     */
    public long getOwedServerTicks()
    {
        return Math.max(0L, this.serverTicks - this.consumedServerTicks);
    }

    /**
     * Take ownership of every authorized-but-unconsumed tick, and report how
     * many (P295 — the integrated-server lockstep).
     *
     * <p>Called once per {@code IntegratedServer.tick} from the server thread:
     * the caller then runs exactly this many vanilla server ticks, so the world
     * advances by the same game time the render clock has handed to the client
     * and not one tick more. Returning 0 is normal and correct — it means no
     * capture frame has been rendered since the previous server tick, so the
     * world must stand still.</p>
     *
     * <p>Clamped to {@link Integer#MAX_VALUE} purely so the caller can loop on an
     * {@code int}; a real recording never owes more than a handful.</p>
     */
    public int claimServerTicks()
    {
        long owed = this.serverTicks - this.consumedServerTicks;

        if (owed <= 0L)
        {
            return 0;
        }

        this.consumedServerTicks += owed;

        return owed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) owed;
    }

    /**
     * The current fractional partial-tick accumulator as an exact double
     * ({@code remainder / captureFps}), in {@code [0, 1)}. The {@link Decision}
     * carries this same value as a float (the vanilla field type); this
     * accessor avoids float rounding for exactness checks.
     */
    public double getTickDeltaExact()
    {
        return (double) this.numerator / this.captureFps;
    }

    /**
     * Advance the clock by one rendered frame and return the decision for it.
     * Call exactly once per {@code RenderTickCounter.beginRenderTick}.
     */
    public Decision tick()
    {
        /* Legacy held cycle: 1..heldFrames; the real frame is the last one.
         * Mirrors CameraExporter.updateHeldFrames(). */
        this.heldFrame = this.heldFrame < this.heldFrames ? this.heldFrame + 1 : 1;

        boolean real = this.heldFrame >= this.heldFrames;

        /* The output frame + sub-frame this render belongs to. For held
         * repeats realCount hasn't advanced yet, so they report the group the
         * upcoming real frame will produce. */
        int subframeIndex = (int) (this.realCount % this.motionBlurFrames);
        int outputFrameIndex = (int) (this.realCount / this.motionBlurFrames);
        boolean motionBlurSubframe = subframeIndex != 0;

        if (!real)
        {
            /* Held repeat: render again at the same game time, no advance. */
            return new Decision(0, this.currentTickDelta(), false, true, motionBlurSubframe, outputFrameIndex, subframeIndex, this.heldFrame);
        }

        /* Real frame: advance game time by exactly 20 / captureFps ticks,
         * carried as integers so a full second sums to exactly 20. */
        this.numerator += TICKS_PER_SECOND;

        int elapsed = (int) (this.numerator / this.captureFps);

        this.numerator -= (long) elapsed * this.captureFps;
        this.serverTicks += elapsed;
        this.realCount++;

        return new Decision(elapsed, this.currentTickDelta(), true, false, motionBlurSubframe, outputFrameIndex, subframeIndex, this.heldFrame);
    }

    private float currentTickDelta()
    {
        return (float) this.numerator / this.captureFps;
    }

    /**
     * One rendered frame's timing decision.
     *
     * <ul>
     * <li>{@link #elapsedTicks} — whole ticks the game world should advance
     * this render (0 on held repeats); the mixin returns this from
     * {@code beginRenderTick}.</li>
     * <li>{@link #tickDelta} — fractional partial-tick accumulator, always in
     * {@code [0, 1)}; copied onto {@code RenderTickCounter.tickDelta}.</li>
     * <li>{@link #canRender} — this render is the real captured output frame;
     * P200 gates the framebuffer readback on it.</li>
     * <li>{@link #heldRepeat} — this render is a held repeat ({@code !canRender}
     * while recording).</li>
     * <li>{@link #motionBlurSubframe} — this belongs to a non-primary sub-frame
     * of its output frame (Aperture's {@code frame % motionblurFrames != 0}).</li>
     * <li>{@link #outputFrameIndex} — 0-based output video frame index
     * (Aperture's {@code getFrame() = floor(frame / motionblurFrames)}).</li>
     * <li>{@link #subframeIndex} — 0-based sub-frame within the output frame,
     * {@code [0, 2^level)}.</li>
     * <li>{@link #heldFrame} — legacy held counter value {@code 1..heldFrames}.</li>
     * </ul>
     */
    public static final class Decision
    {
        public final int elapsedTicks;
        public final float tickDelta;
        public final boolean canRender;
        public final boolean heldRepeat;
        public final boolean motionBlurSubframe;
        public final int outputFrameIndex;
        public final int subframeIndex;
        public final int heldFrame;

        public Decision(int elapsedTicks, float tickDelta, boolean canRender, boolean heldRepeat, boolean motionBlurSubframe, int outputFrameIndex, int subframeIndex, int heldFrame)
        {
            this.elapsedTicks = elapsedTicks;
            this.tickDelta = tickDelta;
            this.canRender = canRender;
            this.heldRepeat = heldRepeat;
            this.motionBlurSubframe = motionBlurSubframe;
            this.outputFrameIndex = outputFrameIndex;
            this.subframeIndex = subframeIndex;
            this.heldFrame = heldFrame;
        }

        @Override
        public String toString()
        {
            return "Decision{elapsed=" + this.elapsedTicks
                + ", tickDelta=" + this.tickDelta
                + ", canRender=" + this.canRender
                + ", heldRepeat=" + this.heldRepeat
                + ", motionBlurSubframe=" + this.motionBlurSubframe
                + ", outputFrame=" + this.outputFrameIndex
                + ", subframe=" + this.subframeIndex
                + ", heldFrame=" + this.heldFrame + "}";
        }
    }
}
