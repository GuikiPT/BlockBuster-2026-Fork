package mchorse.mclib.network.mclib.server;

import mchorse.mclib.config.Config;
import mchorse.mclib.config.ConfigManager;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfig;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Full port of McLib 2.4.3's ServerHandlerConfig (roadmap P26): an op edits
 * server-side config values from the dashboard; the server applies, saves,
 * and re-broadcasts the syncable subset to every client.
 *
 * <p>Op gate lands with the handler (security-relevant). Legacy
 * {@code McLib.proxy.configs} is the {@link Dispatcher#configs} seam, whose
 * default resolves to that same live manager; legacy
 * {@code ConfigManager.synchronizeConfig(config, server, null)} maps to the
 * ported single-arg {@code synchronizeConfig(config)} whose synchronizer seam
 * S2 wires to a send-to-all (legacy passed exception=null, i.e. all players,
 * so behavior is identical).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/server/ServerHandlerConfig.java</p>
 */
public class ServerHandlerConfig extends ServerMessageHandler<PacketConfig>
{
    @Override
    public void run(ServerPlayerEntity player, PacketConfig message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        ConfigManager manager = Dispatcher.configs.get();

        if (manager == null)
        {
            /* Defensive only: the seam's default resolves to the live
             * McLib.proxy.configs (legacy read that static directly), so this
             * branch is unreachable outside a test that nulls the seam. */
            return;
        }

        Config present = manager.modules.get(message.config.id);

        if (present != null)
        {
            present.copy(message.config);
            present.save();

            if (present.hasSyncable())
            {
                ConfigManager.synchronizeConfig(present.filterSyncable());
            }
        }
    }
}
