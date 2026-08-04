package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.network.common.recording.PacketApplyFrame;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Client handler for {@link PacketApplyFrame} (roadmap P116) — applies a single
 * frame to the target living entity and sets its body yaw (which
 * {@code Frame.apply} does not).
 */
public class ClientHandlerApplyFrame extends ClientMessageHandler<PacketApplyFrame>
{
    @Override
    public void run(ClientPlayerEntity player, PacketApplyFrame message)
    {
        Entity entity = player.getWorld().getEntityById(message.getEntityID());

        if (entity instanceof LivingEntity living)
        {
            message.getFrame().apply(living, true);

            /* Frame does not apply bodyYaw, EntityActor.updateDistance() does... TODO refactor this */
            living.bodyYaw = message.getFrame().bodyYaw;
        }
    }
}
