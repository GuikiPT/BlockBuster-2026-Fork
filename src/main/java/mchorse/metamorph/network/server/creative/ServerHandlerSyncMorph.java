package mchorse.metamorph.network.server.creative;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.creative.PacketSyncMorph;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler that writes one edited acquired morph back into the player's
 * acquired list at {@code index} (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/creative/ServerHandlerSyncMorph.java</p>
 */
public class ServerHandlerSyncMorph extends ServerMessageHandler<PacketSyncMorph>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSyncMorph message)
    {
        IMorphing cap = Morphing.get(player);

        if (cap != null && message.morph != null)
        {
            cap.getAcquiredMorphs().set(message.index, message.morph);
        }
    }
}
