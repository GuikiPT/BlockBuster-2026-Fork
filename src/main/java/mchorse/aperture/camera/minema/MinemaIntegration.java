package mchorse.aperture.camera.minema;

/**
 * Minema integration facade (P202). Original class:
 * {@code mchorse.aperture.camera.minema.MinemaIntegration} — kept under its
 * legacy package/name for diff-ability against {@code .tools/legacy-src}, so the
 * ported {@code GuiMinemaPanel} and {@code CameraHandler} call sites diff clean.
 *
 * <p>In 1.12.2 this class was a reflective wrapper around the external Minema
 * mod's {@code MinemaAPI}: {@code isLoaded()} (mod present), {@code isAvailable()}
 * (cached reflective version probe), {@code toggleRecording(boolean)},
 * {@code isRecording()}, {@code openMovies()}, {@code getMessage(Exception)},
 * {@code setName(String)} → {@code VideoHandler.customName}, and
 * {@code setEngineSpeed(float)}. Minema is dead on 1.20.4, so this is now a
 * facade over Blockbuster's <b>built-in</b> recorder
 * ({@code mchorse.blockbuster.client.video.VideoRecorder}, P199–P201).</p>
 *
 * <p><b>Availability delta (documented, S15 P186 / S18):</b> the built-in
 * recorder can always run — a missing {@code ffmpeg} merely switches the sink to
 * the PNG-sequence fallback (P201), it never hides the panel. So
 * {@link #isLoaded()} and {@link #isAvailable()} are constant {@code true}; the
 * legacy {@code minema_not_installed} / {@code minema_wrong_version} panel texts
 * become dead-but-kept (translation-key parity, P8).</p>
 *
 * <p>The GL/render-thread half of the recorder is client-only, so the actual
 * start/stop/open work is delegated to a {@link Backend} seam that the S15/S18
 * client entrypoint installs
 * ({@code mchorse.blockbuster.client.video.MinemaBackend}). The headless /
 * dedicated-server default is a safe no-op, keeping this class plain-JUnit
 * testable and callable from the <b>main</b> source set (where
 * {@code CameraHandler.isApertureAndMinemaLoaded()} reads it).</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/minema/MinemaIntegration.java
 */
public class MinemaIntegration
{
    /**
     * Client-installed recorder backend. Everything GL-facing lives behind this
     * seam so the facade compiles/tests headlessly.
     */
    public interface Backend
    {
        boolean isRecording();

        /**
         * Start (state {@code true}) or stop (state {@code false}) the recorder.
         * Legacy {@code toggleRecording} could throw (Minema surfaced encoder
         * setup errors); the panel catches it and shows a modal, so the throws
         * clause is preserved.
         */
        void toggleRecording(boolean state) throws Exception;

        void setName(String name);

        void openMovies();
    }

    private static final Backend NOOP = new Backend()
    {
        @Override
        public boolean isRecording()
        {
            return false;
        }

        @Override
        public void toggleRecording(boolean state)
        {}

        @Override
        public void setName(String name)
        {}

        @Override
        public void openMovies()
        {}
    };

    /** Installed by the S18 client entrypoint; headless default is the no-op. */
    public static Backend backend = NOOP;

    /**
     * Whether the recorder is present. Always {@code true} — the recorder is
     * bundled (kept as a method for diff-ability with the legacy reflective
     * mod-present probe and because {@code CameraHandler} reads it).
     */
    public static boolean isLoaded()
    {
        return true;
    }

    /**
     * Whether the recorder can run. Always {@code true} — a missing {@code ffmpeg}
     * falls back to a PNG sequence rather than making the recorder unavailable
     * (documented delta from Minema's version-gate).
     */
    public static boolean isAvailable()
    {
        return true;
    }

    public static boolean isRecording()
    {
        return backend.isRecording();
    }

    public static void toggleRecording(boolean state) throws Exception
    {
        backend.toggleRecording(state);
    }

    public static void setName(String name)
    {
        backend.setName(name);
    }

    public static void openMovies()
    {
        backend.openMovies();
    }

    /**
     * Exception message passthrough (legacy pulled a Minema-specific message;
     * the port surfaces the recorder's own message, falling back to
     * {@code toString()} when null so the modal never shows an empty string).
     */
    public static String getMessage(Exception e)
    {
        String message = e.getMessage();

        return message == null ? e.toString() : message;
    }

    /**
     * Legacy engine-speed control (Minema's variable-speed capture). No-op in the
     * port: the P199 fixed-timestep clock owns capture speed (implied by fps).
     * Kept so ported call sites diff clean (it was dormant public API — no
     * in-tree callers in Aperture 1.8.2 / Blockbuster 2.7.2).
     */
    public static void setEngineSpeed(float speed)
    {
        /* fixed-timestep clock owns speed; intentional no-op */
    }
}
