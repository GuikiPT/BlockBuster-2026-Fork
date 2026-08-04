package mchorse.blockbuster.client.textures;

import mchorse.blockbuster.client.compat.iris.IrisPbrGifBridge;
import mchorse.blockbuster.client.compat.iris.PbrFramePacker;
import mchorse.blockbuster.utils.mclib.GifFolder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Single decoded GIF frame texture
 *
 * Port of Blockbuster 2.7.2's {@code mchorse.blockbuster.client.textures.GifFrameTexture}.
 * Owns the GL object for one frame of a {@link GifFolder}'s decoded animation.
 * Legacy uploaded the frame with {@code TextureUtil.uploadTextureImage}; on the
 * modern stack the same is achieved by backing an {@link NativeImageBackedTexture}
 * with a {@link NativeImage} converted from the decoder's {@code BufferedImage}
 * (see {@link NativeImages#fromBufferedImage}).
 *
 * <h3>PBR companion maps (P217.1)</h3>
 *
 * <p>Legacy {@code loadTexture} first tried {@code tryLoadMultiTex()}: when
 * Optifine reported shaders active it packed this frame together with the
 * matching frames of the sibling {@code _n.gif} / {@code _s.gif} files into a
 * single {@code int[]} and handed it to {@code ShadersTex.setupTexture}; any
 * failure fell through to the plain upload above.</p>
 *
 * <p>Here the two maps are ordinary textures built on demand by
 * {@link #getPbrTexture(int)} and served to Iris by {@link IrisPbrGifBridge}.
 * "On demand" is the modern {@code Config.isShaders()} gate: nothing is decoded
 * or uploaded unless a PBR-capable shader pack actually asks. A failure leaves
 * both maps null and Iris uses its own defaults, which carry exactly the
 * {@link PbrFramePacker#NORMAL_FILL} / {@link PbrFramePacker#SPECULAR_FILL}
 * values — the legacy fall-through, preserved.</p>
 */
public class GifFrameTexture extends NativeImageBackedTexture
{
    public GifFolder file;
    public int index;

    /** Lazily built companion maps; see {@link #getPbrTexture(int)}. */
    private NativeImageBackedTexture normal;
    private NativeImageBackedTexture specular;
    private boolean pbrBuilt;

    public GifFrameTexture(GifFolder file, int index)
    {
        super(NativeImages.fromBufferedImage(file.gif.getFrame(index)));

        this.file = file;
        this.index = index;
    }

    /**
     * The normal ({@link PbrFramePacker#NORMAL}) or specular
     * ({@link PbrFramePacker#SPECULAR}) companion map for this frame, built on
     * first request. Returns {@code null} when the frame could not be packed —
     * the caller must degrade, never throw.
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
            int[] packed = PbrFramePacker.packFrame(this.file, this.index);

            if (packed == null)
            {
                return;
            }

            int width = this.file.gif.getFrame(this.index).getWidth();
            int height = this.file.gif.getFrame(this.index).getHeight();

            this.normal = new NativeImageBackedTexture(NativeImages.fromArgb(
                PbrFramePacker.segment(packed, PbrFramePacker.NORMAL, width, height), width, height));
            this.specular = new NativeImageBackedTexture(NativeImages.fromArgb(
                PbrFramePacker.segment(packed, PbrFramePacker.SPECULAR, width, height), width, height));
        }
        catch (Throwable t)
        {
            this.normal = null;
            this.specular = null;
        }
    }

    /**
     * The texture manager closes a frame when the animation is re-registered;
     * the companion maps are owned by this frame and must go with it.
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
