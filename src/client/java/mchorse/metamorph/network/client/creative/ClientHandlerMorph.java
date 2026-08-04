package mchorse.metamorph.network.client.creative;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.creative.PacketMorph;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler applying the owner's own morph (roadmap P55). Force-applies so
 * the client mirrors the server's authoritative morph.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/creative/ClientHandlerMorph.java</p>
 */
public class ClientHandlerMorph extends ClientMessageHandler<PacketMorph>
{
    @Override
    public void run(ClientPlayerEntity player, PacketMorph message)
    {
        IMorphing capability = Morphing.get(player);

        if (capability != null)
        {
            capability.setCurrentMorph(message.morph, player, true);
        }
    }
}
