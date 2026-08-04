package mchorse.metamorph.network.server.survival;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.survival.PacketRemoveMorph;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler removing an acquired morph by index (roadmap P55). Only echoes
 * the packet back to the client when the removal actually happened.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/survival/ServerHandlerRemoveMorph.java</p>
 */
public class ServerHandlerRemoveMorph extends ServerMessageHandler<PacketRemoveMorph>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRemoveMorph message)
    {
        if (Morphing.get(player).remove(message.index))
        {
            Dispatcher.sendTo(message, player);
        }
    }
}
