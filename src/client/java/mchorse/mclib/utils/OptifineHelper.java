package mchorse.mclib.utils;

import mchorse.blockbuster.client.compat.iris.IrisCompat;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;

/**
 * McLib's Optifine bridge (roadmap P217), backed by Iris on 1.20.4.
 *
 * <p>Class and method names are kept verbatim so every ported call-site diffs
 * 1:1 against {@code .tools/legacy-src}; only the backing changes. Legacy
 * reflected {@code net.optifine.shaders.Shaders} directly and cached each
 * lookup in a private {@code ReflectionElement} — that caching contract now
 * lives in {@link IrisCompat}, which this class is a thin facade over.</p>
 *
 * <p>Absence default is {@code false} everywhere: with no shader mod installed
 * the port renders exactly as 1.12.2-without-Optifine did.</p>
 *
 * <p><b>Note the source set.</b> Legacy McLib is a single Forge jar, so this
 * class sat in {@code src/main}; the port splits environments, and both the
 * Iris API and every call-site are client-only. Common code that needs the
 * answer takes it through a supplier seam instead — see
 * {@link mchorse.blockbuster_pack.trackers.MorphTracker#shadowPass}, installed
 * from {@code BlockbusterClient.onInitializeClient}.</p>
 *
 * Legacy source: .tools/legacy-src/mclib/src/main/java/mchorse/mclib/utils/OptifineHelper.java
 */
public class OptifineHelper
{
    private OptifineHelper()
    {}

    /**
     * Whether the shader mod is currently rendering the shadow map.
     *
     * <p>Legacy read the static {@code Shaders.isShadowPass} field; Iris
     * answers the same question through {@code IrisApi.isRenderingShadowPass()}.
     * Both are live reads behind a cached lookup.</p>
     */
    public static boolean isOptifineShadowPass()
    {
        return IrisCompat.isShadowPass();
    }

    /**
     * Legacy invoked {@code Shaders.nextEntity(Entity)} so that shader packs
     * assigned a correct per-entity ID to entities Blockbuster rendered out of
     * band (the {@code renderLast} pass, which drew actors and model blocks
     * <i>after</i> vanilla's entity loop had already advanced Optifine's
     * counter).
     *
     * <p><b>Deliberate no-op on Iris.</b> Iris derives entity IDs inside its
     * own entity-render hooks rather than from a manually advanced counter, so
     * there is nothing to notify — and its v0 API exposes no equivalent. The
     * method is kept, with its legacy name and signature, so the call-sites
     * that will exist once the sorted {@code IRenderLast} pass lands (deferred
     * on {@code TileEntityModel}) read identically to 1.12.2. If out-of-band
     * actor rendering ever <i>does</i> confuse Iris' entity-id uniform, this is
     * the single place to mirror what BBS' {@code EntityVertexMixin} does.</p>
     */
    public static void nextEntity(Entity entity)
    {}

    /**
     * Block-entity twin of {@link #nextEntity(Entity)} (legacy
     * {@code Shaders.nextBlockEntity(TileEntity)}) — a no-op for the same
     * reason.
     */
    public static void nextBlockEntity(BlockEntity blockEntity)
    {}
}
