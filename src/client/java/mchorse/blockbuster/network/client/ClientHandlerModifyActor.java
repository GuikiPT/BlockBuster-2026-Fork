package mchorse.blockbuster.network.client;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.common.PacketModifyActor;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Client handler for {@link PacketModifyActor} (roadmap P119.2, ledger slot 0).
 *
 * <p>1:1 port of 1.12.2 {@code network/client/ClientHandlerModifyActor}: it
 * does <b>not</b> apply the packet, it <b>enqueues</b> it on the actor. The
 * apply happens in {@link EntityActor#turnHead}, which drains
 * {@link EntityActor#modify} once per tick.</p>
 *
 * <p>That indirection is load-bearing. {@code applyPause} mutates the live
 * morph instance the renderer is walking, so applying it on the network thread
 * races the render thread; queueing moves the mutation onto the tick, which is
 * also where legacy's {@code morph.update(this)} runs.</p>
 */
public class ClientHandlerModifyActor extends ClientMessageHandler<PacketModifyActor>
{
    @Override
    public void run(ClientPlayerEntity player, PacketModifyActor message)
    {
        if (player == null || player.getWorld() == null)
        {
            return;
        }

        Entity entity = player.getWorld().getEntityById(message.id);

        if (entity instanceof EntityActor actor)
        {
            actor.modify.add(message);
        }
    }
}
