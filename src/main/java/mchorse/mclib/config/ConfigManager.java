package mchorse.mclib.config;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.client.gui.utils.ValueColors;
import mchorse.mclib.config.json.ConfigParser;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueDouble;
import mchorse.mclib.config.values.ValueFloat;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.config.values.ValueRL;
import mchorse.mclib.config.values.ValueString;
import mchorse.mclib.events.McLibEvents;
import mchorse.mclib.events.RegisterConfigEvent;
import mchorse.mclib.network.ForgeByteBufUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code config/ConfigManager.java} (roadmap P20).
 *
 * <p>The {@link #TYPES} strings are a <b>wire contract</b> (S2 config sync) —
 * do not rename. {@code "colors"} maps to the real {@link ValueColors} (P34; formerly a
 * placeholder until S3).</p>
 *
 * <p>Port notes: legacy {@code register(File)} posted the Forge
 * {@code RegisterConfigEvent} on {@code McLib.EVENT_BUS}; the port dispatches
 * the same event object through {@link #REGISTER_CALLBACKS} (the P22 events
 * layer will formalize this), keeping the legacy ordering register → reload.
 * {@code synchronizeConfig} sent {@code PacketConfig} to every player — it is
 * kept behind the pluggable {@link #synchronizer} hook until S2.</p>
 */
public class ConfigManager
{
    public static final BiMap<String, Class<? extends Value>> TYPES = HashBiMap.<String, Class<? extends Value>>create();

    /**
     * Stand-in for the Forge event bus: subscribers that contribute config
     * modules (legacy {@code @SubscribeEvent RegisterConfigEvent} handlers).
     * Invoked in registration order by {@link #register(File)}.
     */
    public static final List<Consumer<RegisterConfigEvent>> REGISTER_CALLBACKS = new ArrayList<Consumer<RegisterConfigEvent>>();

    /**
     * Stand-in for the legacy "send config to all players" network path.
     * S2 replaces this no-op with the {@code PacketConfig} broadcast.
     */
    public static Consumer<Config> synchronizer = config -> {};

    public final Map<String, Config> modules = new HashMap<String, Config>();

    static
    {
        TYPES.put("boolean", ValueBoolean.class);
        TYPES.put("double", ValueDouble.class);
        TYPES.put("float", ValueFloat.class);
        TYPES.put("int", ValueInt.class);
        TYPES.put("rl", ValueRL.class);
        TYPES.put("string", ValueString.class);
        TYPES.put("colors", ValueColors.class);
    }

    /**
     * Send given config to all players on the server (S2 wires the packet)
     */
    public static void synchronizeConfig(Config config)
    {
        synchronizer.accept(config);
    }

    /**
     * Config value from bytes
     */
    public static Value fromBytes(ByteBuf buffer)
    {
        String key = ForgeByteBufUtils.readUTF8String(buffer);
        String type = ForgeByteBufUtils.readUTF8String(buffer);

        if (type.isEmpty())
        {
            return null;
        }

        try
        {
            Class<? extends Value> clazz = TYPES.get(type);
            Value value = clazz.getConstructor(String.class).newInstance(key);

            value.fromBytes(buffer);

            return value;
        }
        catch (Exception e)
        {}

        return null;
    }

    /**
     * Config value to bytes. Unknown types write an empty type string and skip
     * the payload — {@link #fromBytes(ByteBuf)} then returns null and the
     * child is skipped.
     */
    public static void toBytes(ByteBuf buffer, Value value)
    {
        String type = TYPES.inverse().get(value.getClass());

        ForgeByteBufUtils.writeUTF8String(buffer, value.id);
        ForgeByteBufUtils.writeUTF8String(buffer, type == null ? "" : type);

        if (type != null)
        {
            value.toBytes(buffer);
        }
    }

    public void register(File configs)
    {
        RegisterConfigEvent event = new RegisterConfigEvent(configs);

        for (Consumer<RegisterConfigEvent> callback : REGISTER_CALLBACKS)
        {
            callback.accept(event);
        }

        /* P22: the formal McLib event bus fires after the legacy list, with
         * the same mutable event object (registration order preserved) */
        McLibEvents.REGISTER_CONFIG.invoker().accept(event);

        Config opAccess = event.opAccess.getConfig().serverSide();

        this.modules.put(opAccess.id, opAccess);

        for (Config config : event.modules)
        {
            this.modules.put(config.id, config);
        }

        this.reload();
    }

    public void reload()
    {
        for (Config config : this.modules.values())
        {
            ConfigParser.fromJson(config, config.file);
        }
    }

    public void resetServerValues()
    {
        for (Config config : this.modules.values())
        {
            config.resetServerValues();
        }
    }
}
