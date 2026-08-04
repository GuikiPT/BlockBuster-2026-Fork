package mchorse.blockbuster.client.compat.iris;

import java.io.IOException;

import mchorse.blockbuster.client.textures.GifFrameTexture;
import mchorse.blockbuster.client.textures.GifTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

/**
 * A late-resolving PBR companion texture for an animated GIF skin (P217.1).
 *
 * <p>This is the modern answer to legacy's {@code updateMultiTex}. Optifine hung
 * its shader-texture state on a {@code multiTex} <b>field of the texture
 * object</b>, so Blockbuster had to copy that field from the frame texture onto
 * the {@link GifTexture} wrapper: either object could end up bound, and only one
 * of them carried valid state. Iris keys its PBR maps off the <b>GL texture
 * id</b> instead, and both objects already answer the <i>same</i> id by
 * construction ({@code GifTexture.getGlId()} delegates to the current frame) —
 * so there is no field to copy. What is left is the animation itself: the map
 * Iris samples has to follow the frame the skin is on.</p>
 *
 * <p>Hence this wrapper. It owns nothing; {@link #getGlId()} resolves the frame
 * that is current <i>at bind time</i> and returns that frame's own normal or
 * specular GL id, so one registration serves every frame. It is the same shape
 * as BBS' {@code IrisTextureWrapper} (reference only), which carries a frame
 * {@code index} for exactly this reason.</p>
 *
 * <p>Returning {@link AbstractTexture#DEFAULT_ID} (-1) when anything is missing
 * makes Iris fall back to its own default map, which holds the very values
 * {@link PbrFramePacker#NORMAL_FILL} / {@link PbrFramePacker#SPECULAR_FILL}
 * encode — so the legacy "no companion file" look survives even the failure
 * path.</p>
 */
public class GifPbrTexture extends AbstractTexture
{
    /** The animated wrapper to follow; {@code null} when {@link #frame} is set. */
    public final GifTexture gif;

    /** A fixed frame, when the loader was asked about one frame in isolation. */
    public final GifFrameTexture frame;

    /** {@link PbrFramePacker#NORMAL} or {@link PbrFramePacker#SPECULAR}. */
    public final int type;

    public GifPbrTexture(GifTexture gif, int type)
    {
        this.gif = gif;
        this.frame = null;
        this.type = type;
    }

    public GifPbrTexture(GifFrameTexture frame, int type)
    {
        this.gif = null;
        this.frame = frame;
        this.type = type;
    }

    @Override
    public void load(ResourceManager manager) throws IOException
    {}

    @Override
    public int getGlId()
    {
        GifFrameTexture target = this.frame != null ? this.frame : (this.gif == null ? null : this.gif.currentFrameTexture());

        if (target == null)
        {
            return DEFAULT_ID;
        }

        AbstractTexture map = target.getPbrTexture(this.type);

        return map == null ? DEFAULT_ID : map.getGlId();
    }

    /**
     * The GL objects belong to the {@link GifFrameTexture}s this only points at,
     * and those are closed by the texture manager when the animation is
     * replaced — closing here would delete a live frame's map.
     */
    @Override
    public void close()
    {}
}
