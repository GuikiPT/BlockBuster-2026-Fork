package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketRemoveMorph;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler applying a server-confirmed acquired-morph removal (roadmap
 * P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerRemoveMorph.java</p>
 */
public class ClientHandlerRemoveMorph extends ClientMessageHandler<PacketRemoveMorph>
{
    @Override
    public void run(ClientPlayerEntity player, PacketRemoveMorph message)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing != null)
        {
            morphing.remove(message.index);
        }
    }
}
