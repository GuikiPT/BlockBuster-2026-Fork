package mchorse.blockbuster.common.block;

import net.minecraft.block.BlockState;

/**
 * P97: the full-bright technique for chroma (green screen) blocks, extracted
 * into a pure helper so the branch table is unit-testable headlessly.
 *
 * <p>1.12.2's {@code BlockGreen.getPackedLightmapCoords} hardcoded
 * {@code 15728880} ({@code 15 << 20 | 15 << 4}) — full-bright <b>via a lightmap
 * override, not light emission</b>: the green block still only lit the world at
 * level 1 (see {@code BlockGreen}'s {@code luminance(1)}), but rendered as if
 * fully lit so chroma keying is uniform. 1.20.4 has no per-block
 * {@code getPackedLightmapCoords}; the equivalent seam is the static
 * {@code WorldRenderer.getLightmapCoordinates(BlockRenderView, BlockState,
 * BlockPos)} which {@code BlockModelRenderer} calls on both the flat and smooth
 * lighting paths — {@link mchorse.blockbuster.mixin.client.WorldRendererLightmapMixin}
 * overrides it for chroma states.</p>
 *
 * <p>{@code dim_green} deliberately <b>keeps the real combined light</b> (it
 * "doesn't glow"): it is <i>not</i> intercepted, so the vanilla lightmap path
 * runs and produces exactly the legacy value. Legacy {@code BlockDimGreen}
 * additionally had a slab special-case that sampled the block below when the
 * light was 0 — but the condition tested {@code state.getBlock() instanceof
 * BlockSlab} on the chroma block's <b>own</b> state, which can never be a slab,
 * so the branch is a no-op in practice. It is preserved faithfully in
 * {@link #pack(boolean, boolean, int, int)} (do not "fix" it into a real
 * below-block check) and documented rather than wired live.</p>
 */
public final class ChromaLight
{
    /** {@code 15 << 20 | 15 << 4} — full sky + full block light. */
    public static final int FULLBRIGHT = 15728880;

    private ChromaLight()
    {}

    /**
     * @return whether this block state should be rendered full-bright, i.e. it
     * is a {@link BlockGreen} but not the {@link BlockDimGreen} variant.
     */
    public static boolean isFullbright(BlockState state)
    {
        return state != null && state.getBlock() instanceof BlockGreen && !(state.getBlock() instanceof BlockDimGreen);
    }

    /**
     * Pure branch table mirroring both legacy overrides.
     *
     * @param dim        {@code true} for {@code dim_green}, {@code false} for {@code green}.
     * @param isSlab     legacy {@code state.getBlock() instanceof BlockSlab} — always
     *                   {@code false} for a chroma block; kept for faithful parity.
     * @param worldLight the real combined lightmap value at the position.
     * @param belowLight the combined lightmap value of the block below (only
     *                   consulted in the dead legacy slab branch).
     */
    public static int pack(boolean dim, boolean isSlab, int worldLight, int belowLight)
    {
        if (!dim)
        {
            return FULLBRIGHT;
        }

        /* Legacy BlockDimGreen: i == 0 && state instanceof BlockSlab -> sample
         * below; else the real combined light. The slab test never holds for
         * the chroma block itself, so this stays passthrough in practice. */
        if (worldLight == 0 && isSlab)
        {
            return belowLight;
        }

        return worldLight;
    }
}
