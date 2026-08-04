package mchorse.blockbuster.client.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Bedrock particle blend material.
 *
 * <p>Ids and {@code fromString} fallback are a disk contract preserved verbatim
 * from 1.12.2. The legacy blend/alpha GL state is expressed here in 1.20.4 terms
 * (core profile dropped {@code glAlphaFunc}); the actual shader/{@code RenderLayer}
 * selection that reproduces each material's alpha cutoff lands in P153, which
 * consumes {@link #getAlphaCutoff()}. The legacy {@code beginGL()}/{@code endGL()}
 * method names are kept so P153 call sites diff cleanly against the 1.12.2
 * source; their bodies now issue {@link RenderSystem} blend calls only.</p>
 *
 * <p>Legacy alpha cutoffs (encoded as data, applied via shader choice in P153):
 * OPAQUE {@code 0.0}, ALPHA {@code 0.1}, BLEND {@code 0.0}, ADDITIVE {@code 0.0}.
 * Keeping OPAQUE (cutoff 0) distinct from ALPHA (cutoff 0.1) is why the cutoff is
 * data rather than a hard-coded discard.</p>
 */
public enum BedrockMaterial
{
    OPAQUE("particles_opaque", 0.0F), ALPHA("particles_alpha", 0.1F), BLEND("particles_blend", 0.0F), ADDITIVE("particles_add", 0.0F);

    public final String id;

    /**
     * Alpha-test cutoff the legacy material set via {@code glAlphaFunc(GL_GREATER, x)}.
     * There is no fixed-function alpha test in the core profile — P153 applies this
     * through shader/{@link net.minecraft.client.render.RenderLayer} selection.
     */
    public final float alphaCutoff;

    public static BedrockMaterial fromString(String material)
    {
        for (BedrockMaterial mat : values())
        {
            if (mat.id.equals(material))
            {
                return mat;
            }
        }

        return OPAQUE;
    }

    private BedrockMaterial(String id, float alphaCutoff)
    {
        this.id = id;
        this.alphaCutoff = alphaCutoff;
    }

    /**
     * The alpha-test cutoff this material draws with. See {@link #alphaCutoff}.
     */
    public float getAlphaCutoff()
    {
        return this.alphaCutoff;
    }

    /**
     * Set up the blend state for this material. Legacy set both a blend function
     * and an alpha-test cutoff; the cutoff is now data ({@link #getAlphaCutoff()})
     * applied by P153's layer/shader choice. OPAQUE deliberately still issues its
     * blend function even though it disables blending (state-leak parity with the
     * 1.12.2 renderer).
     */
    public void beginGL()
    {
        switch (this)
        {
            case OPAQUE:
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.disableBlend();
                break;
            case ALPHA:
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.disableBlend();
                break;
            case BLEND:
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.enableBlend();
                break;
            case ADDITIVE:
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                RenderSystem.enableBlend();
                break;
        }
    }

    /**
     * Restore the "leave GL as vanilla particles expect" postcondition (blend
     * disabled, standard src-alpha/one-minus-src-alpha blend function, alpha
     * cutoff 0.1 conceptually). P153's layer bracketing must reproduce the same
     * postcondition.
     */
    public void endGL()
    {
        switch (this)
        {
            case OPAQUE:
            case ALPHA:
            case BLEND:
            case ADDITIVE:
                RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.disableBlend();
                break;
        }
    }
}
