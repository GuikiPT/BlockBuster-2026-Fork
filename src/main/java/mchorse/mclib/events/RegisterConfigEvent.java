package mchorse.mclib.events;

import mchorse.mclib.config.Config;
import mchorse.mclib.config.ConfigBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of McLib 2.4.3's {@code events/RegisterConfigEvent.java} (roadmap P20).
 *
 * <p>Port note: legacy extended Forge's {@code Event} and was posted on
 * {@code McLib.EVENT_BUS}. Until the P22 events layer formalizes the bus, this
 * is a plain data object dispatched through
 * {@code ConfigManager.REGISTER_CALLBACKS}.</p>
 *
 * <p>Path derivation kept verbatim: {@code createBuilder(id)} →
 * {@code <configDir>/<id>/config.json}; two-arg form joins
 * {@code <configDir>/<path>}. (The one-arg overload adds the module twice —
 * harmless legacy quirk, since ConfigManager keys modules by id.)</p>
 */
public class RegisterConfigEvent
{
    public final File configs;
    public List<Config> modules = new ArrayList<Config>();

    public final ConfigBuilder opAccess;

    public RegisterConfigEvent(File configs)
    {
        this.configs = configs;

        this.opAccess = this.createBuilder("op_access", "mclib/op_access.json");
    }

    public ConfigBuilder createBuilder(String id)
    {
        ConfigBuilder builder = this.createBuilder(id, id + "/config.json");

        this.modules.add(builder.getConfig());

        return builder;
    }

    public ConfigBuilder createBuilder(String id, String path)
    {
        ConfigBuilder builder = new ConfigBuilder(id, new File(this.configs, path));

        this.modules.add(builder.getConfig());

        return builder;
    }
}
