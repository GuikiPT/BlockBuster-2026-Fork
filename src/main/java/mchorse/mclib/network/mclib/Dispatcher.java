package mchorse.mclib.network.mclib;

import mchorse.mclib.McLib;
import mchorse.mclib.config.ConfigManager;
import mchorse.mclib.network.AbstractDispatcher;
import mchorse.mclib.network.ChannelLedger;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.network.Side;
import mchorse.mclib.network.mclib.common.PacketAnswer;
import mchorse.mclib.network.mclib.common.PacketBoolean;
import mchorse.mclib.network.mclib.common.PacketConfig;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import mchorse.mclib.network.mclib.common.PacketDropItem;
import mchorse.mclib.network.mclib.common.PacketRequestConfigs;
import mchorse.mclib.network.mclib.common.PacketRequestPermission;
import mchorse.mclib.network.mclib.server.ServerHandlerConfig;
import mchorse.mclib.network.mclib.server.ServerHandlerConfirm;
import mchorse.mclib.network.mclib.server.ServerHandlerDropItem;
import mchorse.mclib.network.mclib.server.ServerHandlerPermissionRequest;
import mchorse.mclib.network.mclib.server.ServerHandlerRequestConfigs;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Supplier;

/**
 * Full port of McLib 2.4.3's mclib-channel Dispatcher (roadmap P26).
 * Registration order follows the legacy discriminator sequence 1:1 (ledger
 * P23.1); CLIENT handlers are referenced by name because they live in the
 * split client source set (see {@code AbstractDispatcher}).
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/Dispatcher.java</p>
 */
public class Dispatcher
{
    private static final String CLIENT = "mchorse.mclib.network.mclib.client.";

    /**
     * The active {@code ConfigManager} instance for the config-sync handlers.
     *
     * <p>Legacy {@code ServerHandlerConfig} / {@code ServerHandlerRequestConfigs}
     * read {@code McLib.proxy.configs} <b>directly</b>; the port keeps a
     * supplier seam so headless tests can swap in a scratch manager, but the
     * <b>default resolves to that exact static</b> — the live manager
     * {@code Blockbuster.registerConfigs()} populates. There is therefore no
     * init-order window in which the server-side config handlers see a null
     * manager and silently early-return.</p>
     */
    public static Supplier<ConfigManager> configs = () -> McLib.proxy.configs;

    public static final AbstractDispatcher DISPATCHER = new AbstractDispatcher(ChannelLedger.MCLIB)
    {
        @Override
        public void register()
        {
            register(PacketDropItem.class, ServerHandlerDropItem.class, Side.SERVER);

            /* Config related packets */
            register(PacketRequestConfigs.class, ServerHandlerRequestConfigs.class, Side.SERVER);
            register(PacketConfig.class, ServerHandlerConfig.class, Side.SERVER);
            register(PacketConfig.class, CLIENT + "ClientHandlerConfig", Side.CLIENT);

            //TODO abstract confirm thing into server to client to server answer thing - see IAnswerRequest etc.
            /* Confirm related packets */
            register(PacketConfirm.class, CLIENT + "ClientHandlerConfirm", Side.CLIENT);
            register(PacketConfirm.class, ServerHandlerConfirm.class, Side.SERVER);

            /* client answer related packets */
            register(PacketAnswer.class, CLIENT + "ClientHandlerAnswer", Side.CLIENT);
            register(PacketBoolean.class, CLIENT + "ClientHandlerBoolean", Side.CLIENT);

            register(PacketRequestPermission.class, ServerHandlerPermissionRequest.class, Side.SERVER);
        }
    };

    private static boolean registered;

    /**
     * Send message to players who are tracking given entity
     */
    public static void sendToTracked(Entity entity, IMessage message)
    {
        DISPATCHER.sendToTracked(entity, message);
    }

    /**
     * Send message to given player
     */
    public static void sendTo(IMessage message, ServerPlayerEntity player)
    {
        DISPATCHER.sendTo(message, player);
    }

    /**
     * Send message to the server
     */
    public static void sendToServer(IMessage message)
    {
        DISPATCHER.sendToServer(message);
    }

    /**
     * Register all the networking messages and message handlers
     * (idempotent — mod init and headless tests may both call it)
     */
    public static synchronized void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        DISPATCHER.register();
    }
}
