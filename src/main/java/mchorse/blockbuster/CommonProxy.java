package mchorse.blockbuster;

import mchorse.blockbuster.api.ModelHandler;
import mchorse.blockbuster.api.ModelPack;
import mchorse.blockbuster.recording.RecordManager;
import mchorse.blockbuster.recording.capturing.DamageControlManager;
import mchorse.blockbuster.recording.scene.SceneManager;
import mchorse.blockbuster.utils.BlockbusterPaths;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Server-scoped holder mirroring legacy {@code CommonProxy} statics
 * (roadmap P109/P111). Legacy kept {@code CommonProxy.manager} (and later
 * {@code CommonProxy.damage}, P113) as mod-lifetime singletons; the world
 * binding happened implicitly through Forge's {@code DimensionManager}
 * statics. On Fabric the server reference is bound in
 * {@code ServerLifecycleEvents.SERVER_STARTING} and cleared (after
 * {@link RecordManager#reset()}) in {@code SERVER_STOPPING} — see
 * {@link Blockbuster#registerServerEvents()}.
 */
public class CommonProxy
{
    /**
     * Record manager (recording + playback state). Mod-lifetime singleton
     * like legacy; {@link RecordManager#reset()} runs on server stop.
     */
    public static RecordManager manager = new RecordManager();

    /**
     * Scene manager (legacy {@code CommonProxy.scenes}). Stub seam until S11
     * (P128) — see {@link mchorse.blockbuster.recording.scene.SceneManager}.
     */
    public static SceneManager scenes = new SceneManager();

    /**
     * Damage control manager (legacy {@code CommonProxy.damage}, P113). The
     * world-edit rollback repository keyed by arbitrary owner objects
     * (recorder instances, scene/playback objects). Mod-lifetime singleton
     * like legacy.
     */
    public static DamageControlManager damage = new DamageControlManager();

    /**
     * The running (integrated or dedicated) server. Null when no world is
     * loaded — legacy code reached it via {@code DimensionManager}.
     */
    public static MinecraftServer server;

    /**
     * Model pack (roadmap P68) — the folder-scan + default/packed registration
     * source. Lazily built by {@link #loadModels(boolean)} on first use (legacy
     * built it in {@code CommonProxy.preLoad}). Mod-lifetime singleton.
     */
    public static ModelPack pack;

    /**
     * Model handler (roadmap P69) — server default is
     * {@link mchorse.blockbuster.api.ModelHandler}; the client overwrites this
     * with a {@code ModelClientHandler} during client init (legacy
     * {@code getHandler()} side split).
     */
    public static ModelHandler models;

    /**
     * Load domain models (as data) from the pack — legacy
     * {@code CommonProxy.loadModels(force)}. Lazily instantiates {@link #pack}
     * and, if unset, a server-side {@link mchorse.blockbuster.api.ModelHandler}.
     */
    public static void loadModels(boolean force)
    {
        if (pack == null)
        {
            pack = new ModelPack();
        }

        if (models == null)
        {
            models = new ModelHandler();
        }

        models.loadModels(pack, force);
    }

    /**
     * Headless-test seam: when set, {@link #saveRoot()} ignores
     * {@link #server} so RecordUtils/RecordManager can be exercised against
     * a temp directory without booting a server.
     */
    public static Path saveRootOverride;

    /**
     * The current world-save root ({@code DimensionManager
     * .getCurrentSaveRootDirectory()} in 1.12.2), or null when no world is
     * loaded.
     */
    public static Path saveRoot()
    {
        if (saveRootOverride != null)
        {
            return saveRootOverride;
        }

        return server == null ? null : BlockbusterPaths.worldRoot(server);
    }
}
