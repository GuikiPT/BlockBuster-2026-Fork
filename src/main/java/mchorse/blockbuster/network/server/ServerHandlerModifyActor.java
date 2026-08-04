package mchorse.blockbuster.network.server;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.common.PacketModifyActor;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketModifyActor} (roadmap S10, P123 close-sync).
 *
 * <p>Injects the received morph + invisibility onto the actor entity via
 * {@link EntityActor#modify}. This is the 1:1 legacy behavior: the handler
 * applies ONLY {@code morph}/{@code invisible} and lets {@code modify(...,
 * true)} notify trackers — the pause fields carried on the wire are applied by
 * the client-side apply-queue path (P119.2), not here.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/.../network/server/ServerHandlerModifyActor.java}.</p>
 */
public class ServerHandlerModifyActor extends ServerMessageHandler<PacketModifyActor>
{
    @Override
    public void run(ServerPlayerEntity player, PacketModifyActor message)
    {
        if (player == null)
        {
            return;
        }

        Entity entity = player.getWorld().getEntityById(message.id);

        if (entity instanceof EntityActor actor)
        {
            actor.modify(message.morph, message.invisible, true);
        }
    }
}
