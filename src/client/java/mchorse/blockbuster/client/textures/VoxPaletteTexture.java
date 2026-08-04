package mchorse.blockbuster.client.textures;

import mchorse.blockbuster.api.formats.vox.data.VoxTexture;
import mchorse.blockbuster.client.compat.iris.IrisPbrGifBridge;
import mchorse.blockbuster.client.compat.iris.PbrFramePacker;
import mchorse.blockbuster.client.compat.iris.VoxPbrPacker;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * The GL texture a VOX limb's palette strip is uploaded into (P217.3).
 *
 * <p>Until this class existed the palette went up as a plain
 * {@link NativeImageBackedTexture}, which is functionally right and
 * <em>identity-less</em>: Iris keys a {@code PBRTextureLoader} on the texture's
 * <b>class</b>, and registering a loader for {@code NativeImageBackedTexture}
 * would claim every programmatically uploaded texture in the game. So the seam
 * that lets {@link IrisPbrGifBridge} recognise a VOX palette is simply a
 * dedicated subclass — the pixels, dimensions and registration are unchanged.</p>
 *
 * <p>It also carries the {@link VoxTexture} the pixels came from, which is what
 * the companion maps are built out of: a flat normal and a specular map filled
 * with the limb's {@link VoxTexture#specular} colour. See {@link VoxPbrPacker}
 * for why that is the faithful port of the legacy "third row" and why the
 * palette itself stays one row tall.</p>
 *
 * <p>Both maps are <b>lazy</b>, exactly as {@link GifFrameTexture#getPbrTexture}
 * is: Iris only asks while a PBR-capable pack is loaded, so a vanilla install
 * never allocates them (the modern {@code Config.isShaders()} gate). A failure
 * leaves them null and Iris falls back to its own defaults, which hold the very
 * values {@link PbrFramePacker#NORMAL_FILL}/{@link PbrFramePacker#SPECULAR_FILL}
 * encode.</p>
 */
public class VoxPaletteTexture extends NativeImageBackedTexture
{
    /** The palette data these pixels were built from. */
    public final VoxTexture palette;

    /** Lazily built companion maps; see {@link #getPbrTexture(int)}. */
    private NativeImageBackedTexture normal;
    private NativeImageBackedTexture specular;
    private boolean pbrBuilt;

    public VoxPaletteTexture(VoxTexture palette)
    {
        super(NativeImages.fromArgb(palette.buildPixels(), palette.getWidth(), palette.getHeight()));

        this.palette = palette;
    }

    /**
     * The normal ({@link PbrFramePacker#NORMAL}) or specular
     * ({@link PbrFramePacker#SPECULAR}) companion map for this palette, built on
     * first request. Returns {@code null} when the maps could not be built — the
     * caller must degrade, never throw.
     */
    public NativeImageBackedTexture getPbrTexture(int type)
    {
        if (!this.pbrBuilt)
        {
            this.pbrBuilt = true;
            this.buildPbrTextures();
        }

        return type == PbrFramePacker.SPECULAR ? this.specular : this.normal;
    }

    private void buildPbrTextures()
    {
        try
        {
            int width = this.palette.getWidth();
            int height = this.palette.getHeight();

            this.normal = new NativeImageBackedTexture(
                NativeImages.fromArgb(VoxPbrPacker.normalMap(this.palette), width, height));
            this.specular = new NativeImageBackedTexture(
                NativeImages.fromArgb(VoxPbrPacker.specularMap(this.palette), width, height));
        }
        catch (Throwable t)
        {
            this.normal = null;
            this.specular = null;
        }
    }

    /**
     * The palette is closed when the limb is deleted or its identifier is
     * re-registered ({@code TextureRegistry.register}/{@code delete}); the
     * companion maps are owned by it and must go with it.
     */
    @Override
    public void close()
    {
        super.close();

        if (this.normal != null)
        {
            this.normal.close();
            this.normal = null;
        }

        if (this.specular != null)
        {
            this.specular.close();
            this.specular = null;
        }
    }
}
