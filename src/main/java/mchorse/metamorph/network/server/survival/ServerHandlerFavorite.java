package mchorse.metamorph.network.server.survival;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.survival.PacketFavorite;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler toggling a morph favorite (roadmap P55). Echoes the packet
 * back so the client GUI's favorite state is server-confirmed.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/survival/ServerHandlerFavorite.java</p>
 */
public class ServerHandlerFavorite extends ServerMessageHandler<PacketFavorite>
{
    @Override
    public void run(ServerPlayerEntity player, PacketFavorite message)
    {
        Morphing.get(player).favorite(message.index);
        Dispatcher.sendTo(message, player);
    }
}
