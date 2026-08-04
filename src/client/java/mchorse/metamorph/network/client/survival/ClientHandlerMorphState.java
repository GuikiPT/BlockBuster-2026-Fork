package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.ClientMorphFlow;
import mchorse.metamorph.client.MetamorphHudWiring;
import mchorse.metamorph.network.common.survival.PacketMorphState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Client handler syncing the owner's morph-state bookkeeping (roadmap P55).
 *
 * <p>Quirk (P53): overwrites the client-side {@link EntityMorph} inner entity's
 * id with the server's dummy id so both sides agree; then mirrors the squid-air
 * drown counters.</p>
 *
 * <p>P225: the counters now go through {@link ClientMorphFlow#applyMorphState},
 * which also mirrors them onto {@link MetamorphHudWiring#HUD} so the replacement
 * bubble bar is correct on the very frame the packet lands rather than one
 * client tick later.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerMorphState.java</p>
 */
public class ClientHandlerMorphState extends ClientMessageHandler<PacketMorphState>
{
    @Override
    public void run(ClientPlayerEntity player, PacketMorphState message)
    {
        IMorphing capability = Morphing.get(player);

        if (capability == null)
        {
            return;
        }

        AbstractMorph morph = capability.getCurrentMorph();

        if (morph instanceof EntityMorph)
        {
            Entity entity = ((EntityMorph) morph).getEntity(player.getWorld());

            if (entity != null)
            {
                entity.setId(message.entityID);
            }
        }

        ClientMorphFlow.applyMorphState(capability, MetamorphHudWiring.HUD, message.hasSquidAir, message.squidAir);
    }
}
