package mchorse.blockbuster.client.particles;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster_pack.BlockbusterSection;

/**
 * S22 batch U-K — the Snowstorm preset library's production lifecycle.
 *
 * <p>P154 shipped {@link BedrockLibrary} and P230 wired
 * {@code SnowstormClient.install()}, but <b>nothing ever built the library the
 * client reads from</b>: {@code ClientProxy.particles} had no production
 * assignment at all, only a lazy one inside the Snowstorm editor panel. So
 * {@code SnowstormClient.getScheme} returned {@code null} for every preset,
 * {@code BedrockEmitter.setScheme(null, …)} ran an empty emitter, and every
 * Snowstorm morph in the world rendered <i>nothing</i> — silently, unless the
 * user happened to open the particle dashboard that session.</p>
 *
 * <p>This class owns the three legacy wirings:</p>
 *
 * <ol>
 *   <li><b>Construction at init.</b> Legacy {@code CommonProxy.load} line 124:
 *       {@code this.particles = new BedrockLibrary(new File(configFile,
 *       "models/particles"))}. The port keeps the library client-side (1.12.2's
 *       {@code CommonProxy} placement predates split source sets) and takes the
 *       folder from {@link BlockbusterPaths#particles()} rather than rebuilding
 *       the path — see the S13 plan's implementation sketch. The
 *       {@link BedrockLibrary} constructor {@code mkdirs()}es the folder, which
 *       is what creates {@code config/blockbuster/models/particles} on a fresh
 *       install (P7 folder-layout parity).</li>
 *   <li><b>Reload on world join.</b> Legacy {@code ModelHandler.onClientConnect}
 *       (lines 104–105) ran {@code proxy.loadModels(false)} <i>and</i>
 *       {@code proxy.particles.reload()}; the port's JOIN hook did only the
 *       first half. {@link mchorse.blockbuster.BlockbusterClient#onClientJoin()}
 *       now calls {@link #reload()} right after it, in legacy order. This is the
 *       call that fills {@link BedrockLibrary#presets} — the constructor only
 *       fills {@code factory}, so before the first join even the built-in
 *       presets are absent from the map {@code getScheme} reads.</li>
 *   <li><b>Reload on creative-picker refresh.</b> Legacy
 *       {@code BlockbusterSection.update} line 262 called
 *       {@code particles.reload()} between the model reload and the structure
 *       request. {@code BlockbusterSection} lives in {@code src/main} (the
 *       section is registered on both sides) and cannot see the client-only
 *       library, so it declares a {@link BlockbusterSection#particleReloader}
 *       seam — the same static-seam idiom as its
 *       {@link BlockbusterSection#skinResolver} — which {@link #install()}
 *       fills.</li>
 * </ol>
 *
 * <p>Legacy had no file watcher on this folder; none is added.</p>
 */
public final class ParticleLibraryWiring
{
    private ParticleLibraryWiring()
    {}

    /**
     * Client-init wiring: build the library (once) and hand the common-side
     * creative section its reload seam.
     *
     * <p>Construction is guarded on {@code null} deliberately. The library
     * instance is identity-significant — {@code SnowstormClient.reloadIfStale}
     * compares a live emitter's {@link BedrockScheme} against the one the
     * library currently holds — so a second writer replacing the holder would
     * make every live emitter rebuild. There is exactly one writer, and it is
     * this one.</p>
     */
    public static void install()
    {
        if (ClientProxy.particles == null)
        {
            ClientProxy.particles = new BedrockLibrary(BlockbusterPaths.particles().toFile());
        }

        BlockbusterSection.particleReloader = ParticleLibraryWiring::reload;
    }

    /**
     * Rescan {@code config/blockbuster/models/particles} — legacy
     * {@code Blockbuster.proxy.particles.reload()}. Null-safe so the seam stays
     * total on a dedicated server and in headless tests, where the holder is
     * never built.
     */
    public static void reload()
    {
        if (ClientProxy.particles != null)
        {
            ClientProxy.particles.reload();
        }
    }
}
