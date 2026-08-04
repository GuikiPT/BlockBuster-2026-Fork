package mchorse.mclib;

import mchorse.mclib.config.ConfigManager;

/**
 * Minimal port of McLib 2.4.3's {@code CommonProxy} (roadmap P44 seam;
 * S1 P22 owns the full proxy/registration flow).
 *
 * <p>Legacy {@code McLib.proxy} was the Forge sided-proxy holding the live
 * {@link ConfigManager} ({@code preInit} called
 * {@code configs.register(configFolder)} after mods posted their
 * {@code RegisterConfigEvent}s). The S3 config panel (P44's
 * {@code GuiConfigPanel}) compiles against {@code McLib.proxy.configs}
 * verbatim, so this holder exists now with an empty manager.</p>
 *
 * <p>P22 landed: {@code Blockbuster.registerConfigs()} seeds
 * {@code ConfigManager.REGISTER_CALLBACKS} (McLib's own config, then Aperture's,
 * then Blockbuster's) and calls {@code McLib.proxy.configs.register(configDir)}
 * during mod init, so this manager is live from startup.</p>
 */
public class CommonProxy
{
    public ConfigManager configs = new ConfigManager();
}
