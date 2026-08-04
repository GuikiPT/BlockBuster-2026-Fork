package mchorse.blockbuster.client.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Locates the {@code ffmpeg} binary and probes its availability (P201), porting
 * Aperture/BBS's forgiving path heuristics: users routinely point the config at
 * a directory, a {@code bin} folder, or (on Windows) a path missing
 * {@code .exe}.
 *
 * <p><b>Divergence from BBS:</b> BBS's {@code findFFMPEG} builds the {@code bin}
 * sub-path with {@code File.pathSeparator} ({@code ':'} / {@code ';'}) where it
 * meant {@link File#separator} — a bug that makes the {@code bin/ffmpeg} probe
 * never match. Fixed here.</p>
 */
public final class FfmpegLocator
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    private FfmpegLocator()
    {}

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Resolve a user-configured path to a concrete ffmpeg {@link File}, trying
     * (in order): the path itself; {@code <dir>/ffmpeg[.exe]} and
     * {@code <dir>/bin/ffmpeg[.exe]} when it is a directory; {@code <path>.exe}
     * on Windows when the bare path does not exist. Returns the best candidate
     * even if it is not a file, so callers can fall back cleanly.
     */
    public static File findFfmpeg(String path)
    {
        return findFfmpeg(path, isWindows());
    }

    /** OS-parameterized variant (testable per-platform without touching the JVM OS). */
    public static File findFfmpeg(String path, boolean windows)
    {
        File file = new File(path);
        String exe = windows ? "ffmpeg.exe" : "ffmpeg";

        if (file.isDirectory())
        {
            File bin = new File(file, exe);

            if (bin.isFile())
            {
                return bin;
            }

            bin = new File(file, "bin" + File.separator + exe);

            if (bin.isFile())
            {
                return bin;
            }
        }
        else if (windows && !file.exists())
        {
            File withExt = new File(path + ".exe");

            if (withExt.exists())
            {
                return withExt;
            }
        }

        return file;
    }

    /**
     * The executable string to hand {@link ProcessBuilder}: the resolved
     * absolute path when {@link #findFfmpeg} landed on a real file, otherwise the
     * raw configured value (so a bare {@code "ffmpeg"} on {@code PATH} still
     * works).
     */
    public static String resolve(String configuredPath)
    {
        File file = findFfmpeg(configuredPath);

        return file.isFile() ? file.getAbsolutePath() : configuredPath;
    }

    /**
     * Whether the resolved binary actually runs, by exec'ing {@code -version}
     * once. Never throws — a missing/broken binary returns {@code false} and the
     * recorder switches to the PNG fallback (total-reader spirit: capture never
     * hard-fails because a binary is absent).
     */
    public static boolean checkAvailable(String configuredPath)
    {
        String binary = resolve(configuredPath);

        try
        {
            ProcessBuilder builder = new ProcessBuilder(binary, "-version");

            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = builder.start();

            if (!process.waitFor(10, TimeUnit.SECONDS))
            {
                process.destroyForcibly();

                return false;
            }

            return process.exitValue() == 0;
        }
        catch (Exception e)
        {
            LOGGER.warn("ffmpeg not available at '{}' ({}); video capture will fall back to a PNG sequence", binary, e.getMessage());

            return false;
        }
    }
}
