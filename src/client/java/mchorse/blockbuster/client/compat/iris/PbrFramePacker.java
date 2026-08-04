package mchorse.blockbuster.client.compat.iris;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.utils.mclib.GifFolder;

/**
 * PBR companion-map packer for animated GIF skins (roadmap P217.1).
 *
 * <p>This is the headless half of the shader-era GIF stack: the pure function
 * legacy {@code GifFrameTexture.tryLoadMultiTex()} performed before it handed
 * pixels to Optifine. Given one decoded animation frame plus the matching frames
 * of its {@code _n.gif} / {@code _s.gif} siblings, it produces the legacy
 * three-segment {@code int[]}:</p>
 *
 * <pre>
 *   [0 .. w*h)         base frame RGB
 *   [w*h .. 2*w*h)     normal map   (or {@link #NORMAL_FILL} when absent)
 *   [2*w*h .. 3*w*h)   specular map (or {@link #SPECULAR_FILL} when absent)
 * </pre>
 *
 * <p>The array layout is retained verbatim <b>even though nothing on 1.20.4
 * consumes a packed triple</b>: it is what the goldens pin, and it is the one
 * artifact that can be diffed against the 1.12.2 source line for line. Iris
 * wants two separate textures, so {@link #segment} slices the packed array back
 * apart at the point of upload. Parity target is the <i>sampled values</i>, not
 * the packing (see plan/S21 P217.1 "Quirks").</p>
 *
 * <h2>The fill constants are load-bearing</h2>
 *
 * <p>{@link #NORMAL_FILL} is {@code 0xFF7F7FFF} — ARGB for the flat
 * tangent-space normal (127, 127, 255). {@link #SPECULAR_FILL} is {@code 0},
 * i.e. fully rough / non-metal / zero emission, <b>not</b> white. Both match
 * what Iris' own {@code PBRType.NORMAL/SPECULAR.getDefaultValue()} answer, which
 * is why the modern bridge can serve them as ordinary textures instead of
 * special-casing "no companion map".</p>
 *
 * <h2>Deviations from the legacy body (both are totality fixes)</h2>
 *
 * <ol>
 *   <li><b>Frame-count mismatch.</b> Legacy indexed the companion GIF with the
 *       <i>base</i> frame index ({@code normal.gif.getFrame(this.index)}); a
 *       companion with fewer frames threw {@code IndexOutOfBoundsException} out
 *       of {@code ArrayList.get} and killed the texture load. Here the index is
 *       clamped into the companion's own range and a warning is logged once per
 *       file (see {@link #siblingFrame}).</li>
 *   <li><b>Dimension mismatch.</b> Legacy read the sibling with the <i>base</i>
 *       width/height ({@code frame.getRGB(0, 0, width, height, …)}); a
 *       <i>smaller</i> companion threw {@code ArrayIndexOutOfBoundsException}. A
 *       smaller companion is now treated as absent (fill + warn). A
 *       <i>larger</i> companion still crops to the base's top-left corner,
 *       exactly as {@code getRGB} did — that path was never a crash and users
 *       may depend on it.</li>
 * </ol>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/textures/GifFrameTexture.java</p>
 */
public final class PbrFramePacker
{
    /** Flat tangent-space normal, ARGB {@code (255, 127, 127, 255)}. */
    public static final int NORMAL_FILL = 0xFF7F7FFF;

    /** Fully rough / non-metal specular. Zero, not white. */
    public static final int SPECULAR_FILL = 0;

    /** Legacy companion-map file suffixes. */
    public static final String NORMAL_SUFFIX = "_n.gif";
    public static final String SPECULAR_SUFFIX = "_s.gif";

    /** Segment indices into a packed array. */
    public static final int BASE = 0;
    public static final int NORMAL = 1;
    public static final int SPECULAR = 2;

    /** One-time warning latches (legacy logged nothing at all — see class doc). */
    private static boolean warnedFrameCount;
    private static boolean warnedDimensions;

    private PbrFramePacker()
    {}

    /**
     * Legacy sibling-name derivation, quirk included: it strips the
     * <b>last four characters</b> of the path rather than the literal
     * {@code ".gif"} extension, then appends the suffix. A path that does not
     * end in {@code .gif} therefore still loses four characters — that is what
     * 1.12.2 did ({@code path.substring(0, path.length() - 4)}) and the naming
     * of every companion file in the wild follows from it.
     *
     * <p>Totality guard: a path shorter than four characters would have thrown
     * {@code StringIndexOutOfBoundsException}; it now yields just the suffix.</p>
     */
    public static String siblingPath(String filePath, String suffix)
    {
        if (filePath == null)
        {
            return suffix;
        }

        int cut = Math.max(0, filePath.length() - 4);

        return filePath.substring(0, cut) + suffix;
    }

    /**
     * Resolve the {@code _n.gif} companion of a GIF skin. Returns a
     * {@link GifFolder} whose {@link GifFolder#exists()} answers whether the
     * companion decoded — the same probe legacy used.
     */
    public static GifFolder normalSibling(GifFolder file)
    {
        return new GifFolder(siblingPath(file.getFilePath(), NORMAL_SUFFIX));
    }

    /** Resolve the {@code _s.gif} companion of a GIF skin. */
    public static GifFolder specularSibling(GifFolder file)
    {
        return new GifFolder(siblingPath(file.getFilePath(), SPECULAR_SUFFIX));
    }

    /**
     * Full legacy {@code tryLoadMultiTex} data path for one frame: discover the
     * two siblings by name and pack them against the base frame.
     *
     * @return the packed triple, or {@code null} when the base GIF itself did
     *         not decode (legacy threw {@code FileNotFoundException} from
     *         {@code loadTexture} in that case; the caller falls back to a plain
     *         upload, which is the same observable outcome).
     */
    public static int[] packFrame(GifFolder file, int index)
    {
        if (file == null || !file.exists())
        {
            return null;
        }

        BufferedImage frame = siblingFrame(file, index);

        if (frame == null)
        {
            return null;
        }

        GifFolder normal = normalSibling(file);
        GifFolder specular = specularSibling(file);

        return pack(frame,
            normal.exists() ? siblingFrame(normal, index) : null,
            specular.exists() ? siblingFrame(specular, index) : null);
    }

    /**
     * Pack one frame plus its optional companion frames into the legacy
     * three-segment array. {@code null} (or dimension-incompatible) companions
     * are replaced by the fill constants.
     */
    public static int[] pack(BufferedImage frame, BufferedImage normal, BufferedImage specular)
    {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int pixels = width * height;
        int[] aint = new int[pixels * 3];

        frame.getRGB(0, 0, width, height, aint, 0, width);

        if (usable(normal, width, height))
        {
            normal.getRGB(0, 0, width, height, aint, pixels, width);
        }
        else
        {
            Arrays.fill(aint, pixels, pixels * 2, NORMAL_FILL);
        }

        if (usable(specular, width, height))
        {
            specular.getRGB(0, 0, width, height, aint, pixels * 2, width);
        }
        else
        {
            Arrays.fill(aint, pixels * 2, pixels * 3, SPECULAR_FILL);
        }

        return aint;
    }

    /**
     * Slice one segment ({@link #BASE} / {@link #NORMAL} / {@link #SPECULAR})
     * out of a packed array, so it can be uploaded as its own texture. Returns
     * the fill-only segment when {@code packed} is short or {@code null} rather
     * than throwing (totality).
     */
    public static int[] segment(int[] packed, int segment, int width, int height)
    {
        int pixels = width * height;
        int[] out = new int[pixels];
        int from = pixels * segment;

        if (packed == null || packed.length < from + pixels)
        {
            Arrays.fill(out, segment == SPECULAR ? SPECULAR_FILL : segment == NORMAL ? NORMAL_FILL : 0);

            return out;
        }

        System.arraycopy(packed, from, out, 0, pixels);

        return out;
    }

    /**
     * Read frame {@code index} of a decoded GIF, clamping the index into the
     * file's own frame range. Legacy passed the base index straight through, so
     * a companion GIF with fewer frames crashed the texture load; the clamp is
     * the P217.1 defensive port ("clamp index, log once").
     */
    public static BufferedImage siblingFrame(GifFolder file, int index)
    {
        if (file == null || !file.exists())
        {
            return null;
        }

        int count = file.gif.getFrameCount();

        if (count <= 0)
        {
            return null;
        }

        int clamped = index < 0 ? 0 : (index >= count ? count - 1 : index);

        if (clamped != index && !warnedFrameCount)
        {
            Blockbuster.LOGGER.warn("A GIF companion map has fewer frames than its skin ({}: {} frames, asked for {}); clamping to the last frame.",
                file.getFilePath(), count, index);
            warnedFrameCount = true;
        }

        try
        {
            return file.gif.getFrame(clamped);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * A companion frame is usable when it covers the base frame's rectangle.
     * Smaller companions are dropped (legacy crashed); larger ones crop.
     */
    private static boolean usable(BufferedImage image, int width, int height)
    {
        if (image == null)
        {
            return false;
        }

        if (image.getWidth() < width || image.getHeight() < height)
        {
            if (!warnedDimensions)
            {
                Blockbuster.LOGGER.warn("A GIF companion map is smaller ({}x{}) than its skin ({}x{}); using the flat default instead.",
                    image.getWidth(), image.getHeight(), width, height);
                warnedDimensions = true;
            }

            return false;
        }

        return true;
    }

    /** Test-only: drop the one-time warning latches. */
    public static void resetWarnings()
    {
        warnedFrameCount = false;
        warnedDimensions = false;
    }
}
