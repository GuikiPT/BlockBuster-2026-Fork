package mchorse.blockbuster.client.video;

/**
 * P199 — the standalone clock switch that decouples the render clock from wall
 * time while capture is active.
 *
 * <p>This is deliberately independent of the capture pipeline (P200+
 * {@code VideoRecorder}, a parallel branch): the recorder flips this switch on
 * at the start of a recording and off at the end, and the
 * {@code RenderTickCounterMixin} consults <i>only</i> this class. Keeping the
 * switch here — rather than reaching into the recorder — means the fixed-timestep
 * clock can be developed, tested, and reasoned about on its own.</p>
 *
 * <p>SEAM(P200): {@code VideoRecorder.startRecording(...)} will call
 * {@link #start(int, int, int)} and {@code stopRecording()} will call
 * {@link #stop()}; the capture hook will read {@link #canRender()} to decide
 * whether the just-rendered frame is a real output frame to read back.</p>
 *
 * <p>The render clock itself lives entirely on the render thread, exactly like
 * vanilla's {@code RenderTickCounter} — {@link #tick()} and {@link #canRender()}
 * are render-thread-only. The <b>one</b> exception is
 * {@link #claimServerTicks()} (S22 P295), which the integrated-server thread
 * calls to find out how much game time it has been authorized to run; see
 * {@link CaptureTiming}'s threading note.</p>
 */
public final class CaptureClock
{
    /**
     * The value {@link #claimServerTicks()} returns when no recording is running,
     * telling the integrated server to tick on wall time exactly like vanilla.
     * Deliberately distinct from {@code 0}, which means "recording, but this
     * server tick has not been authorized yet — stand still".
     */
    public static final int NOT_CAPTURING = -1;

    /**
     * Active timing while recording; {@code null} means normal (wall-time) play.
     *
     * <p>{@code volatile} because the integrated-server thread reads it through
     * {@link #claimServerTicks()}: without it the server could keep seeing a
     * stale {@code null} (never lockstepping) or a stale instance (never
     * releasing) after the render thread flipped the switch.</p>
     */
    private static volatile CaptureTiming timing;

    /** Whether the most recent recorded render is a real output frame (P200 gate). */
    private static boolean canRender;

    private CaptureClock()
    {}

    /**
     * Begin fixed-timestep playback. From now until {@link #stop()}, the render
     * clock advances by exactly {@code 1/fps} of a second per output frame,
     * regardless of encode/wall speed.
     *
     * @param fps             output video frame rate (must be positive)
     * @param heldFrames      Minema held-frames (repeats per real frame; clamped to &ge; 1)
     * @param motionBlurLevel motion-blur doublings (clamped to &ge; 0)
     */
    public static void start(int fps, int heldFrames, int motionBlurLevel)
    {
        timing = new CaptureTiming(fps, heldFrames, motionBlurLevel);
        canRender = false;
    }

    /**
     * Return the render clock to wall time. Safe to call when not active, and
     * safe to call twice.
     *
     * <p><b>Unconditional, in both halves</b> (S22 P295). Dropping the timing
     * releases the render clock <i>and</i> the integrated server in the same
     * store: the next {@link #claimServerTicks()} answers
     * {@link #NOT_CAPTURING}, so the server is back on wall time on its very
     * next tick. Any ticks the clock had authorized but the server had not run
     * yet are <b>dropped, not paid</b> — the same start/stop asymmetry
     * {@code MinemaBackend.toggleRecording} documents. Paying them would burst
     * the world forward by the debt the instant the take ended, which is a
     * visible jump; dropping them costs nothing, because no frame after the last
     * captured one is encoded. What must never happen is the third option —
     * leaving the server owing ticks nobody will ever authorize, i.e. a
     * permanently frozen world — and that is exactly what makes this
     * unconditional rather than guarded on {@code isRecording()}.</p>
     */
    public static void stop()
    {
        timing = null;
        canRender = true;
    }

    /** Whether fixed-timestep playback is currently driving the render clock. */
    public static boolean isActive()
    {
        return timing != null;
    }

    /** The active timing core, or {@code null} when not recording. */
    public static CaptureTiming timing()
    {
        return timing;
    }

    /**
     * Whether the most recently rendered frame is a real output frame that
     * should be captured (P200). Meaningless / left at its previous value when
     * {@link #isActive()} is false.
     */
    public static boolean canRender()
    {
        return canRender;
    }

    /**
     * Advance the clock by one rendered frame. Called by
     * {@code RenderTickCounterMixin} on {@code beginRenderTick}; updates the
     * {@link #canRender()} gate and returns the frame's decision (or
     * {@code null} when not recording, so the mixin leaves vanilla timing
     * untouched).
     */
    public static CaptureTiming.Decision tick()
    {
        if (timing == null)
        {
            return null;
        }

        CaptureTiming.Decision decision = timing.tick();

        canRender = decision.canRender;

        return decision;
    }

    /**
     * The integrated server's half of the fixed-timestep clock (S22 P295): how
     * many vanilla server ticks it is authorized to run right now.
     *
     * <p>Called once per {@code IntegratedServer.tick}, from the server thread,
     * by {@code IntegratedServerCaptureMixin} — the counterpart of
     * {@link #tick()} on the render thread. The contract is three-valued:</p>
     *
     * <ul>
     * <li>{@link #NOT_CAPTURING} — no recording. The caller must run its single
     * ordinary wall-clock tick; nothing about vanilla timing changes.</li>
     * <li>{@code 0} — recording, but the render clock has not authorized any
     * game time since the last server tick (it is rendering a held repeat, or a
     * frame that fell inside the current tick, or simply taking longer than
     * 50 ms to encode). The server must run <b>no</b> ticks: standing still is
     * how the world stays in step with the footage.</li>
     * <li>{@code n > 0} — run exactly {@code n} ticks. More than one happens
     * whenever the render clock outruns wall time (a high frame rate against a
     * low video frame rate), which is the direction a "run at most one tick per
     * pass" gate would silently starve.</li>
     * </ul>
     *
     * <p>The returned ticks are marked consumed by this call, so a caller that
     * asks and then fails to run them has dropped that game time — ask once per
     * server tick and run the loop to completion.</p>
     */
    public static int claimServerTicks()
    {
        CaptureTiming current = timing;

        if (current == null)
        {
            return NOT_CAPTURING;
        }

        return current.claimServerTicks();
    }
}
