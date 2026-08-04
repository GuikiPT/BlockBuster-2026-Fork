package mchorse.blockbuster.client.video;

/**
 * Sink-selection rule for the P203 alpha (transparent) capture path: an
 * alpha-carrying frame ({@link VideoFormat#BGRA}) must never be piped into an
 * ffmpeg template whose <b>output</b> pixel format cannot carry alpha (e.g. the
 * default {@code yuv420p} H.264 template) — that would silently drop the channel.
 * When the configured template can't carry alpha, the recorder falls back to the
 * PNG sequence (always RGBA-capable) with a logged warning; an alpha-capable
 * template (ProRes 4444, {@code yuva444p10le}) takes the ffmpeg path.
 *
 * <p>Kept as a pure predicate so the decision is headlessly testable without
 * spawning ffmpeg (P203 {@code AlphaSinkSelectionTest}).</p>
 */
public final class AlphaSinkPolicy
{
    private AlphaSinkPolicy()
    {}

    /**
     * Whether an ffmpeg argument template encodes to a pixel format that carries
     * an alpha channel. Reads the <b>last</b> {@code -pix_fmt} occurrence (the
     * output format; the first {@code -pix_fmt} describes the raw {@code -i -}
     * input) and tests it against the known alpha-capable families.
     *
     * @return {@code true} only when an output {@code -pix_fmt} is present and
     *         names an alpha format; {@code false} for a non-alpha format or when
     *         no output pixel format can be identified (fail safe: refuse alpha).
     */
    public static boolean templateSupportsAlpha(String template)
    {
        if (template == null)
        {
            return false;
        }

        String[] tokens = template.split(" ");
        String outputFmt = null;

        for (int i = 0; i + 1 < tokens.length; i++)
        {
            if ("-pix_fmt".equals(tokens[i]))
            {
                outputFmt = tokens[i + 1];
            }
        }

        return isAlphaPixelFormat(outputFmt);
    }

    /**
     * Whether an ffmpeg pixel-format name carries an alpha channel. Covers the
     * families the port's templates use (planar YUV+A, packed RGBA/BGRA/ARGB/ABGR,
     * grayscale+alpha) rather than every ffmpeg format — the selection only needs
     * to distinguish "our alpha preset" from "our opaque preset".
     */
    public static boolean isAlphaPixelFormat(String fmt)
    {
        if (fmt == null)
        {
            return false;
        }

        return fmt.startsWith("yuva")
            || fmt.startsWith("rgba")
            || fmt.startsWith("bgra")
            || fmt.startsWith("argb")
            || fmt.startsWith("abgr")
            || fmt.startsWith("ya")
            || fmt.startsWith("gbrap");
    }

    /**
     * Whether a recording with the given params must fall back to the PNG
     * sequence instead of the given ffmpeg template: true iff the params request
     * alpha but the template can't carry it.
     */
    public static boolean shouldFallbackToPng(VideoParams params, String template)
    {
        return params.format().hasAlpha() && !templateSupportsAlpha(template);
    }
}
