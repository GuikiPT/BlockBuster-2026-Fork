package mchorse.blockbuster.api.formats.vox.data;

import java.util.Arrays;

/**
 * VOX palette texture data (roadmap P78).
 *
 * <p>Port of Blockbuster 2.7.2's {@code api/formats/vox/data/VoxTexture}. The
 * legacy class extended {@code DynamicTexture} (a GL-backed texture) and built
 * a {@code palette.length × 1} pixel row from the document palette. Here the
 * <em>pixel math</em> is separated out into this GL-free data class so it is
 * headless-testable; the client VOX renderer ({@code ModelVoxRenderer}) uploads
 * {@link #buildPixels()} into a {@code NativeImageBackedTexture}.</p>
 *
 * <p><b>The "3-row layout" — settled in S21/P217.3.</b> Legacy's {@code specular}
 * fill branch ({@code tex.length == 3 * palette.length}) was read here as a
 * three-<em>row</em> texture, {@code [palette | ? | specular]}, that a shader
 * path would switch on. That reading is <b>wrong</b>, and P217.3 proved it
 * against the Optifine source: legacy always allocated
 * {@code DynamicTexture(max(palette.length, 1), 1)} and never changed the height
 * — what tripled the buffer was Optifine's own patched {@code DynamicTexture},
 * which allocates {@code new int[width * height * 3]} holding base, normal and
 * specular back to back and uploads the three thirds as three separate GL
 * textures. With height 1 the branch is therefore an "is Optifine installed"
 * probe, not a row check, and the "rows" are the segments of one packed triple.
 * (Vanilla 1.12.2 allocates {@code width * height}, so without Optifine the
 * branch is dead — which is what this port reproduces.)</p>
 *
 * <p>Consequently {@link #rows} <b>must stay {@link #ROWS_LEGACY}</b>. Raising
 * it to {@link #ROWS_SHADER} would produce a {@code width × 3} image that the
 * VOX mesher's full-height V coordinate samples straight through, for a layout
 * no modern shader loader reads. The constant is kept only because it names the
 * legacy segment count; the shader path is
 * {@code client.compat.iris.VoxPbrPacker} + {@code VoxPaletteTexture}, which
 * serve the normal and specular segments to Iris as their own textures and
 * leave this buffer exactly as 1.12.2 wrote it.</p>
 *
 * <p>Segment 1 (the normal third) is deliberately never written here — legacy
 * left it at whatever {@code ShadersTex.initDynamicTexture} had filled it with,
 * which is {@code -8421377} == {@code 0xFF7F7FFF}, the flat tangent-space
 * normal. {@code VoxPbrPacker.normalMap} is that value.</p>
 */
public class VoxTexture
{
    /**
     * Row count of the palette texture. {@code 1} is the legacy allocation and
     * the only one any code path may use; {@code 3} is the segment count of
     * Optifine's packed {@code [base | normal | specular]} buffer, kept as a
     * named constant (and exercised by test) but <b>never</b> to be assigned to
     * {@link #rows} in production — see the class javadoc and P217.3.
     */
    public static final int ROWS_LEGACY = 1;
    public static final int ROWS_SHADER = 3;

    public final int[] palette;
    public final int specular;

    /**
     * Allocation switch — how many rows {@link #buildPixels()} produces.
     * Everything keeps {@link #ROWS_LEGACY}: S21/P217.3 established that even
     * the shader path must, because the segments legacy fed Optifine are not
     * rows of this texture (class javadoc).
     */
    public int rows = ROWS_LEGACY;

    public VoxTexture(int[] palette, int specular)
    {
        this.palette = palette;
        this.specular = specular;
    }

    public VoxTexture(int[] palette, int specular, int rows)
    {
        this(palette, specular);

        this.rows = rows;
    }

    /** Texture width — legacy {@code Math.max(palette.length, 1)}. */
    public int getWidth()
    {
        return Math.max(this.palette.length, 1);
    }

    /** Texture height — the {@link #rows} allocation switch. */
    public int getHeight()
    {
        return Math.max(this.rows, 1);
    }

    /**
     * Build the ARGB pixel buffer. Mirrors legacy {@code updatePalette()}: a
     * {@code max(palette.length, 1)}-wide buffer filled with the palette values,
     * and — when the buffer is exactly three palettes long — the last third
     * filled with the limb's specular colour.
     */
    public int[] buildPixels()
    {
        int[] tex = new int[this.getWidth() * this.getHeight()];

        for (int i = 0; i < this.palette.length; i++)
        {
            tex[i] = this.palette[i];
        }

        if (tex.length == 3 * this.palette.length)
        {
            Arrays.fill(tex, 2 * this.palette.length, tex.length, this.specular);
        }

        return tex;
    }
}
