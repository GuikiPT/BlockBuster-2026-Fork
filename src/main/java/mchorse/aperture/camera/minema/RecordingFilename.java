package mchorse.aperture.camera.minema;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Pure filename resolver for the recording panel (P202), extracted from
 * {@code GuiMinemaPanel.getFilename} + its ghost-text drawable + the tracking
 * export naming, so the naming rules are headlessly testable
 * ({@code RecordingFilenameTest}).
 *
 * <p>Rules (verbatim from 1.12.2):</p>
 * <ul>
 *   <li>an explicit (non-empty) name field wins;</li>
 *   <li>else, when {@code minema.default_profile_name} is on, use the profile's
 *       destination filename, with {@code "-" + (fixtureIndex + 1)} appended in
 *       FIXTURE mode when a fixture is selected (note: legacy appends even when
 *       {@code indexOf} returned {@code -1}, yielding {@code "-0"} — preserved);</li>
 *   <li>else empty (Minema then applied its own timestamp — the port applies the
 *       {@link #timestamp} below itself so the recorder's name matches the ghost
 *       text the user saw).</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/GuiMinemaPanel.java (getFilename / ghost drawable / stop)
 */
public final class RecordingFilename
{
    /** Legacy ghost/timestamp pattern (SimpleDateFormat). */
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH.mm.ss";

    /**
     * Resolve the panel filename.
     *
     * @param explicit             the name field text (may be empty/null)
     * @param defaultProfileName   {@code Aperture.minemaDefaultProfileName}
     * @param profileFilename      the profile destination filename (used only
     *                             when {@code defaultProfileName})
     * @param fixtureMode          whether the panel is in FIXTURE mode
     * @param fixturePresent       whether a timeline fixture is selected
     * @param fixtureIndexZeroBased {@code profile.fixtures.indexOf(fixture)}
     */
    public static String get(String explicit, boolean defaultProfileName, String profileFilename,
        boolean fixtureMode, boolean fixturePresent, int fixtureIndexZeroBased)
    {
        if (explicit != null && !explicit.isEmpty())
        {
            return explicit;
        }

        if (!defaultProfileName)
        {
            return "";
        }

        String text = profileFilename == null ? "" : profileFilename;

        if (fixtureMode && fixturePresent)
        {
            text += "-" + (fixtureIndexZeroBased + 1);
        }

        return text;
    }

    /** The ghost-text / stop-time timestamp for the given epoch millis. */
    public static String timestamp(long millis)
    {
        return new SimpleDateFormat(TIMESTAMP_PATTERN).format(new Date(millis));
    }

    /**
     * The tracking-JSON export name on a clean stop: {@code <filename>.json}, or a
     * <b>fresh</b> {@code <timestamp>.json} when the filename is empty (legacy took
     * a new timestamp at stop time, not the ghost value shown at start).
     */
    public static String exportJsonName(String filename, long millisAtStop)
    {
        String base = (filename == null || filename.isEmpty()) ? timestamp(millisAtStop) : filename;

        return base + ".json";
    }
}
