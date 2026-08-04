package mchorse.metamorph.network.server.creative;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.creative.PacketClearAcquired;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler that clears all of a player's acquired morphs (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/creative/ServerHandlerClearAcquired.java</p>
 */
public class ServerHandlerClearAcquired extends ServerMessageHandler<PacketClearAcquired>
{
    @Override
    public void run(ServerPlayerEntity player, PacketClearAcquired message)
    {
        IMorphing cap = Morphing.get(player);

        if (cap != null)
        {
            cap.removeAcquired();
        }
    }
}
