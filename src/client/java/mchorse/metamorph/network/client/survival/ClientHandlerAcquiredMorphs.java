package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketAcquiredMorphs;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler that wholesale-replaces the owner's acquired morph list
 * (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerAcquiredMorphs.java</p>
 */
public class ClientHandlerAcquiredMorphs extends ClientMessageHandler<PacketAcquiredMorphs>
{
    @Override
    public void run(ClientPlayerEntity player, PacketAcquiredMorphs message)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing != null)
        {
            morphing.setAcquiredMorphs(message.morphs);
        }
    }
}
