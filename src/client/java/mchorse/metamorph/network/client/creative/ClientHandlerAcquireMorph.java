package mchorse.metamorph.network.client.creative;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.ClientMorphFlow;
import mchorse.metamorph.client.MetamorphHudWiring;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler storing a newly acquired morph (roadmap P55) and raising its
 * HUD toast (roadmap P225).
 *
 * <p>Legacy body verbatim: {@code morphing.acquireMorph(message.morph)} followed
 * by {@code ClientProxy.morphOverlay.add(message.morph)}. Both halves go through
 * {@link ClientMorphFlow#acquire}, which keeps the legacy quirk that the toast
 * is pushed <b>unconditionally</b> — re-acquiring a morph you already own still
 * pops a notification.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/creative/ClientHandlerAcquireMorph.java</p>
 */
public class ClientHandlerAcquireMorph extends ClientMessageHandler<PacketAcquireMorph>
{
    @Override
    public void run(ClientPlayerEntity player, PacketAcquireMorph message)
    {
        ClientMorphFlow.acquire(Morphing.get(player), MetamorphHudWiring.OVERLAY, message.morph);
    }
}
