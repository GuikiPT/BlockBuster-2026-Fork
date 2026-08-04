package mchorse.mclib;

import mchorse.mclib.client.gui.utils.KeybindConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

/**
 * Client-side static holder (partial port of McLib 2.4.3's
 * {@code ClientProxy}, roadmap P39 slice).
 *
 * <p>Legacy created {@code public static KeybindConfig keybinds} in
 * {@code preInit} and inserted it directly into
 * {@code configs.modules} (no registration event) so keybind overrides
 * persist and appear in the config GUI. The port initializes it lazily on
 * first {@link #getKeybinds()} — the field is touched from {@code Keybind}
 * constructors, which run headless in tests, so construction must be
 * total: a missing/unreadable {@code config/mclib/keybinds.json} loads as
 * empty.</p>
 *
 * <p>{@link #init()} performs legacy {@code ClientProxy.init}'s last line —
 * {@code this.configs.modules.put(keybinds.id, keybinds)} — so the keybinds
 * module shows up in the config panel and GUI rebinding persists. Like legacy
 * it runs <b>after</b> {@code ConfigManager.register(...)}, so the manager's
 * {@code reload()} never re-parses {@code keybinds.json} (the config loads
 * itself in its constructor).</p>
 */
public class ClientProxy
{
    public static KeybindConfig keybinds;

    /** Legacy {@code McLib.proxy.configFolder} — the run dir's config folder. */
    public static File configFolder;

    /**
     * Legacy {@code ClientProxy.init}'s
     * {@code this.configs.modules.put(keybinds.id, keybinds)} — insert the
     * keybinds config as a module of the live {@link McLib#proxy}'s manager.
     * Called from the client entrypoint after the main entrypoint registered
     * the config tree. Idempotent (an id-keyed map re-put).
     */
    public static void init()
    {
        KeybindConfig config = getKeybinds();

        McLib.proxy.configs.modules.put(config.id, config);
    }

    public static KeybindConfig getKeybinds()
    {
        if (keybinds == null)
        {
            keybinds = new KeybindConfig(getConfigFolder());
        }

        return keybinds;
    }

    public static File getConfigFolder()
    {
        if (configFolder == null)
        {
            try
            {
                configFolder = FabricLoader.getInstance().getConfigDir().toFile();
            }
            catch (Exception e)
            {
                /* Headless fallback (no loader game dir) */
                configFolder = new File(System.getProperty("java.io.tmpdir"), "mclib-config");
            }
        }

        return configFolder;
    }
}
