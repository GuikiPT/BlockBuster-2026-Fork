package mchorse.mclib.network.mclib.server;

import mchorse.mclib.config.Config;
import mchorse.mclib.config.ConfigManager;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketConfig;
import mchorse.mclib.network.mclib.common.PacketRequestConfigs;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Full port of McLib 2.4.3's ServerHandlerRequestConfigs (roadmap P26):
 * an op pulls every module's server-side values for dashboard editing.
 * Op gate lands with the handler (security-relevant). Legacy
 * {@code McLib.proxy.configs} is the {@link Dispatcher#configs} seam, whose
 * default resolves to that same live manager.
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/server/ServerHandlerRequestConfigs.java</p>
 */
public class ServerHandlerRequestConfigs extends ServerMessageHandler<PacketRequestConfigs>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestConfigs message)
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

        for (Config config : manager.modules.values())
        {
            Config serverSide = config.filterServerSide();

            if (!serverSide.values.isEmpty())
            {
                Dispatcher.sendTo(new PacketConfig(serverSide), player);
            }
        }
    }
}
