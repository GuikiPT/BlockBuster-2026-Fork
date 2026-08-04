package mchorse.blockbuster.client.video;

import mchorse.blockbuster.Blockbuster;

/**
 * The {@code screenshot.*} config category (S22 <b>P298</b>): defaults, the
 * <b>live accessors</b> the capture path reads, and the one pure decision the
 * vanilla-F2 mixin asks.
 *
 * <p><b>Why this exists.</b> S18 <b>P204</b> shipped the transparent still
 * itself — the readback, the RGBA encode, the naming/de-dup scheme and two
 * triggers ({@code ScreenshotKeyHandler}'s world keybind and the model editor's
 * F2) — but no config at all. The world keybind defaults to
 * {@code GLFW_KEY_UNKNOWN}, so on a fresh install the feature had exactly zero
 * reachable triggers outside the model editor and nothing in the Blockbuster
 * settings screen so much as mentioned it. P298 is the config surface: an
 * enable switch, a "vanilla F2 takes the transparent shot instead" switch, and
 * an output-folder override.</p>
 *
 * <p><b>The constants are not the wiring</b> — same rule, and the same reason, as
 * {@link VideoConfig}: javac <b>constant-folds</b> a {@code static final
 * boolean}, so a capture path that read {@code DEFAULT_TRANSPARENT} would
 * compile down to a literal and the option would be unreachable no matter what
 * the config file said (that is precisely the S22/P250 defect this category was
 * written to avoid repeating). The {@code DEFAULT_*} fields below exist for one
 * purpose: to be handed to the {@code Value*} constructors in
 * {@link mchorse.blockbuster.Blockbuster#onConfigRegister} as the registered
 * defaults, so the file default and the headless fallback cannot drift.
 * <b>Never read a {@code DEFAULT_*} constant from capture code</b> — read the
 * accessor, or the setting silently stops being configurable.
 * {@code ScreenshotConfigWiringTest} lints exactly that.</p>
 *
 * <p>1.12.2 had no screenshot code of any kind (verified by source grep across
 * {@code blockbuster-1.12} and the legacy deps), so this whole category — like
 * {@code video} — is a documented <b>port addition</b> appended after every
 * legacy category, and a 2.7.2 {@code config/blockbuster/config.json} still
 * round-trips 1:1 (pinned by {@code ConfigRoundTripTest}).</p>
 */
public final class ScreenshotConfig
{
    private ScreenshotConfig()
    {}

    /* ---------------------------------------------------------------- */
    /* Live accessors — every capture-path read goes through these.      */
    /* ---------------------------------------------------------------- */

    /**
     * {@code screenshot.transparent} — the feature switch for Blockbuster's
     * transparent stills. Off: {@link ScreenshotCapture#requestWorldCapture()}
     * queues nothing and vanilla F2 is left completely alone, i.e. the mod is
     * out of the screenshot business entirely.
     */
    public static boolean transparent()
    {
        return Blockbuster.screenshotTransparent.get();
    }

    /**
     * {@code screenshot.replace_vanilla} — let the vanilla screenshot key (F2)
     * take the transparent shot instead of an opaque one, so the feature is
     * usable without hand-binding {@code key.blockbuster.screenshot_transparent}
     * (which P204 left unbound). Default <b>off</b>: parity first — with it off
     * F2 behaves exactly as vanilla, byte for byte.
     */
    public static boolean replacesVanillaScreenshot()
    {
        return Blockbuster.screenshotReplaceVanilla.get();
    }

    /**
     * {@code screenshot.export_path} — empty means
     * {@code <run dir>/screenshots/blockbuster} (P204's default). Same shape and
     * same trim/blank rules as {@code video.export_path}.
     */
    public static String exportPath()
    {
        return Blockbuster.screenshotExportPath.get();
    }

    /**
     * The whole vanilla-F2 decision, as a pure function — the only thing
     * {@code ScreenshotRecorderMixin} is allowed to think.
     *
     * <p>A {@code HEAD}-cancel on {@code ScreenshotRecorder.saveScreenshot} is a
     * seam every vanilla screenshot passes through, so it has to be a strict
     * no-op unless all four conditions hold, and it must fall through to vanilla
     * (never silently swallow the press) whenever one does not:</p>
     *
     * <ul>
     *   <li>the feature is on ({@link #transparent()}), and</li>
     *   <li>the user asked for the takeover ({@link #replacesVanillaScreenshot()}), and</li>
     *   <li>there is a world to capture — on the title screen or a server list
     *       there is no world render, so nothing would ever grab the frame and
     *       the press would vanish, and</li>
     *   <li>no screen is open. Two reasons: the transparent grab happens at
     *       {@code WorldRenderEvents.LAST}, i.e. deliberately <i>before</i> the
     *       HUD and the screen, so an F2 pressed inside a GUI would produce a
     *       shot that does not show what the user was looking at; and the model
     *       editor binds its <b>own</b> F2 (P204's editor variant), which must
     *       keep working untouched.</li>
     * </ul>
     */
    public static boolean interceptsVanillaScreenshot(boolean hasWorld, boolean screenOpen)
    {
        return transparent() && replacesVanillaScreenshot() && hasWorld && !screenOpen;
    }

    /* ---------------------------------------------------------------- */
    /* Registered defaults — handed to the Value* constructors ONLY.     */
    /* ---------------------------------------------------------------- */

    /**
     * Default for {@code screenshot.transparent}: <b>on</b>. This is not "a new
     * behaviour switched on by default" — with {@link #DEFAULT_REPLACE_VANILLA}
     * off, the only trigger it unlocks is the P204 keybind, which ships unbound,
     * so a fresh install behaves identically to P204. Shipping it off instead
     * would mean a user who binds the key gets silence.
     */
    public static final boolean DEFAULT_TRANSPARENT = true;

    /**
     * Default for {@code screenshot.replace_vanilla}: <b>off</b>. Taking over F2
     * is a visible change to a vanilla key, so it is opt-in, exactly like
     * {@code general.watch_files}.
     */
    public static final boolean DEFAULT_REPLACE_VANILLA = false;

    /** Default for {@code screenshot.export_path}: empty = {@code screenshots/blockbuster}. */
    public static final String DEFAULT_EXPORT_PATH = "";
}
