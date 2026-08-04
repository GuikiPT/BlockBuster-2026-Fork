package mchorse.aperture.camera.minema;

/**
 * Pure start/end tick range calculator for the recording panel (P202), extracted
 * from {@code GuiMinemaPanel.startRecording} so the FULL / FIXTURE / CUSTOM math,
 * the {@code end - start <= 0} silent abort, and the "set start" /
 * "set duration" workflow are headlessly testable ({@code RecordingRangeTest}).
 *
 * <p>All values are {@code int} ticks, matching the legacy panel exactly — the
 * legacy code cast {@code profile}/{@code fixture} {@code long} durations to
 * {@code int} when computing {@code start}/{@code end}, so the cast is preserved
 * here (a recording longer than {@code Integer.MAX_VALUE} ticks is not a real
 * scenario, and the truncation is load-bearing for parity).</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/GuiMinemaPanel.java (startRecording / calculateLeft / calculateRight)
 */
public final class RecordingRange
{
    public final int start;
    public final int end;

    public RecordingRange(int start, int end)
    {
        this.start = start;
        this.end = end;
    }

    /**
     * Legacy {@code end - start <= 0} → silently abort (no modal). A range is
     * recordable only when it spans at least one tick.
     */
    public boolean isValid()
    {
        return this.end - this.start > 0;
    }

    /**
     * CUSTOM mode: {@code start = left}, {@code end = start + right} (the panel's
     * two trackpads).
     */
    public static RecordingRange custom(int left, int right)
    {
        int start = left;

        return new RecordingRange(start, start + right);
    }

    /** FULL mode: the whole profile ({@code start = 0}, {@code end = duration}). */
    public static RecordingRange full(long profileDuration)
    {
        return new RecordingRange(0, (int) profileDuration);
    }

    /**
     * FIXTURE mode: {@code start = profile.calculateOffset(fixture)},
     * {@code end = start + fixture.getDuration()} for the currently open fixture
     * panel.
     */
    public static RecordingRange fixture(long offset, long duration)
    {
        int start = (int) offset;

        return new RecordingRange(start, (int) (start + duration));
    }

    /**
     * "Set start" (legacy {@code calculateLeft}): move {@code left} to the
     * timeline cursor while <b>preserving the previous end tick</b> — the new
     * {@code right} becomes the distance from the new left to the old end. This
     * is the load-bearing "set start then set duration" workflow (the panel's
     * trackpads hold {@code double}s, so this operates in {@code double}).
     *
     * @return {@code [newLeft, newRight]}
     */
    public static double[] setStart(double oldLeft, double oldRight, int timelineValue)
    {
        /* Legacy computes the old end as an int cast of (left + right). */
        int oldEnd = (int) (oldLeft + oldRight);
        double newLeft = timelineValue;

        return new double[] {newLeft, oldEnd - newLeft};
    }

    /**
     * "Set duration" (legacy {@code calculateRight}): {@code right = timeline -
     * left}.
     */
    public static double setDuration(double left, int timelineValue)
    {
        return timelineValue - left;
    }
}
