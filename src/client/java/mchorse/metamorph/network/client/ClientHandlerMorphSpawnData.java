package mchorse.metamorph.network.client;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.entity.EntityMorph;
import mchorse.metamorph.network.common.PacketMorphSpawnData;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Morph ghost spawn-data client handler (roadmap P56.1, port addition) — the
 * yarn replacement for legacy {@code EntityMorph.readSpawnData}. Applies the
 * owner + morph to a ghost the client just started tracking; until it arrives
 * the ghost has no morph and {@code RenderMorph} draws nothing.
 *
 * @see PacketMorphSpawnData
 */
public class ClientHandlerMorphSpawnData extends ClientMessageHandler<PacketMorphSpawnData>
{
    @Override
    public void run(ClientPlayerEntity player, PacketMorphSpawnData message)
    {
        Entity entity = player.getWorld().getEntityById(message.entity);

        if (entity instanceof EntityMorph ghost)
        {
            ghost.applySpawnData(message);
        }
    }
}
