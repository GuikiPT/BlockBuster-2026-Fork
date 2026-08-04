package mchorse.blockbuster.network.server;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.network.common.recording.PacketApplyFrame;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketApplyFrame} (roadmap P116).
 *
 * <p>OP-gated. Applies the frame to any {@code LivingEntity} by id, manually
 * sets body yaw (a known legacy TODO — {@code Frame.apply} does not apply body
 * yaw), then <b>rebroadcasts the same packet to ALL players</b> (not just the
 * trackers), exactly like 1.12.2.</p>
 */
public class ServerHandlerApplyFrame extends ServerMessageHandler<PacketApplyFrame>
{
    @Override
    public void run(ServerPlayerEntity player, PacketApplyFrame packet)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Entity entity = player.getWorld().getEntityById(packet.getEntityID());

        if (entity instanceof LivingEntity living)
        {
            Frame frame = packet.getFrame();

            frame.apply(living, true);

            /* Frame does not apply bodyYaw, EntityActor.updateDistance() does... TODO refactor this */
            living.bodyYaw = frame.bodyYaw;

            Dispatcher.sendToAll(packet);
        }
    }
}
