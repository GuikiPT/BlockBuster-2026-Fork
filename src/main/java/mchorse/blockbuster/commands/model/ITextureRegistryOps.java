package mchorse.blockbuster.commands.model;

import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Seam over the runtime texture manager for the {@code /model report} and
 * {@code /model clear} command cores (roadmap P73).
 *
 * <p>Both commands' file-walk / path-resolution halves are pure and live in the
 * common source set; the pieces that touch the live GL texture registry — the
 * per-image "loaded / loaded but missing" status lines of {@code report} and the
 * dynamic-texture eviction of {@code clear} — are implemented against the S7
 * runtime texture registry (roadmap P87) by
 * {@code mchorse.blockbuster.client.commands.ClientTextureRegistryOps}. The
 * {@link #NOOP} implementation remains for headless callers that have no
 * texture map at all (no textures known, nothing to evict).</p>
 *
 * <p>Legacy sources:
 * {@code blockbuster-1.12/.../commands/model/SubCommandModelReport.java}
 * (texture-manager {@code getTexture} + {@code MISSING_TEXTURE} check) and
 * {@code .../SubCommandModelClear.java} (bb-domain eviction +
 * {@code ModelExtrudedLayer} clearing).</p>
 */
public interface ITextureRegistryOps
{
    /**
     * Per-texture status for the {@code /model report} annotation. Mirrors the
     * legacy check against {@code Minecraft.getMinecraft().renderEngine
     * .getTexture(location)}:
     *
     * <ul>
     *   <li>{@link Status#ABSENT} — no texture registered under the key
     *       ({@code getTexture} returned {@code null}); legacy appended
     *       nothing.</li>
     *   <li>{@link Status#MISSING} — the key resolves to the missing-texture
     *       sentinel ({@code == TextureUtil.MISSING_TEXTURE}); legacy appended
     *       {@code ", loaded but missing"}.</li>
     *   <li>{@link Status#LOADED} — a real texture is registered; legacy
     *       appended {@code ", loaded"}.</li>
     * </ul>
     */
    Status reportStatus(ResourceLocation location);

    /**
     * The {@code /model clear} eviction. Evicts every texture whose domain is
     * one of the Blockbuster dynamic-texture domains
     * ({@code c.s}, {@code s&b}, {@code b.a}, {@code http}, {@code https}) and
     * whose resource path starts with {@code prefix}, deleting the GL texture
     * (except the missing-texture sentinel) and clearing the matching extruded
     * layers. When {@code prefix} is empty every extruded layer is cleared
     * wholesale; otherwise the layers are cleared per evicted texture — exactly
     * the legacy branch.
     */
    void clearTextures(String prefix);

    enum Status
    {
        ABSENT, LOADED, MISSING
    }

    /**
     * Headless stand-in: no texture is known and nothing is evicted. The
     * {@code report} output therefore carries no texture-status suffix (legacy
     * behaviour when a skin is not yet loaded), and {@code clear} is a no-op.
     * Used by tests and by any caller with no client texture map.
     */
    ITextureRegistryOps NOOP = new ITextureRegistryOps()
    {
        @Override
        public Status reportStatus(ResourceLocation location)
        {
            return Status.ABSENT;
        }

        @Override
        public void clearTextures(String prefix)
        {}
    };
}
