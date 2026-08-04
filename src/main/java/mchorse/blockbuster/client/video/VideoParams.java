package mchorse.blockbuster.client.video;

import java.io.File;

/**
 * Immutable description of one recording (P200/P201): resolution, timing, pixel
 * format, output location, name, and optional audio track. Produced from the
 * {@code video.*} config category (registered in S19 P208 — this class holds no
 * config wiring so it stays headlessly testable) plus the panel's chosen
 * filename (P202).
 *
 * <p>Even-dimension clamping happens here, at construction: yuv420p encoding
 * requires even width/height, so odd values are rounded <b>down</b> to the
 * nearest even number (never below 2). This is a documented port behavior —
 * 1.12.2 delegated resolution to Minema.</p>
 */
public final class VideoParams
{
    /** Motion-blur filter fragment, appended once per blur level (P201). */
    static final String MOTION_BLUR_FILTER = ",tblend=all_mode=average,framestep=2";

    /** Base filter — GL readback is bottom-up, so every recording flips (P201). */
    static final String BASE_FILTER = "vflip";

    private final int width;
    private final int height;
    private final int fps;
    private final int motionBlur;
    private final int heldFrames;
    private final VideoFormat format;
    private final File exportDir;
    private final String name;
    private final File audioTrack;

    public VideoParams(int width, int height, int fps, int motionBlur, int heldFrames,
        VideoFormat format, File exportDir, String name, File audioTrack)
    {
        this.width = clampEven(width);
        this.height = clampEven(height);
        this.fps = Math.max(1, fps);
        this.motionBlur = Math.max(0, motionBlur);
        this.heldFrames = Math.max(1, heldFrames);
        this.format = format == null ? VideoFormat.BGR : format;
        this.exportDir = exportDir;
        this.name = name;
        this.audioTrack = audioTrack;
    }

    /**
     * Round {@code value} down to the nearest even number, floored at 2. Odd
     * custom resolutions otherwise break yuv420p encoding (P200 quirk).
     */
    public static int clampEven(int value)
    {
        int v = Math.max(2, value);

        return v - (v & 1);
    }

    public int width()
    {
        return this.width;
    }

    public int height()
    {
        return this.height;
    }

    public int fps()
    {
        return this.fps;
    }

    public int motionBlur()
    {
        return this.motionBlur;
    }

    public int heldFrames()
    {
        return this.heldFrames;
    }

    public VideoFormat format()
    {
        return this.format;
    }

    public File exportDir()
    {
        return this.exportDir;
    }

    public String name()
    {
        return this.name;
    }

    public File audioTrack()
    {
        return this.audioTrack;
    }

    public boolean hasAudio()
    {
        return this.audioTrack != null;
    }

    /** Bytes in one frame ({@code width * height * bytesPerPixel}). */
    public int frameByteSize()
    {
        return this.format.byteSize(this.width, this.height);
    }

    /**
     * The capture frame rate handed to ffmpeg's {@code -r}: base fps scaled by
     * {@code 2^motionBlur}, because motion blur renders at N× fps and lets the
     * {@code tblend}/{@code framestep} filter chain average pairs back down to
     * the base rate (BBS technique, reproduced).
     */
    public float captureFrameRate()
    {
        return this.fps * (1 << this.motionBlur);
    }

    /**
     * The {@code %FILTERS%} value: {@code vflip} plus one
     * {@code tblend=all_mode=average,framestep=2} per motion-blur level. Contains
     * no spaces, so substitution after tokenization is safe (P201).
     */
    public String filters()
    {
        StringBuilder sb = new StringBuilder(BASE_FILTER);

        for (int i = 0; i < this.motionBlur; i++)
        {
            sb.append(MOTION_BLUR_FILTER);
        }

        return sb.toString();
    }
}
