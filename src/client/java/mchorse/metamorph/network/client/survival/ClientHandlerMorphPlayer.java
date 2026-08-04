package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketMorphPlayer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Client handler applying a tracked player's morph by entity id (roadmap P55).
 *
 * <p>Port note: legacy resolved the {@code IMorphing} through
 * {@code entity.getCapability(MorphingProvider.MORPHING_CAP)}; the rewrite reads
 * it off the P52 component via {@link Morphing#get(PlayerEntity)}. The entity
 * lookup is null-guarded (total reader — a morph packet for an entity that has
 * not yet spawned client-side is dropped rather than crashing).</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerMorphPlayer.java</p>
 */
public class ClientHandlerMorphPlayer extends ClientMessageHandler<PacketMorphPlayer>
{
    @Override
    public void run(ClientPlayerEntity player, PacketMorphPlayer message)
    {
        Entity entity = player.getWorld().getEntityById(message.id);

        if (!(entity instanceof PlayerEntity))
        {
            return;
        }

        IMorphing capability = Morphing.get((PlayerEntity) entity);

        if (capability != null)
        {
            capability.setCurrentMorph(message.morph, (PlayerEntity) entity, true);
        }
    }
}
