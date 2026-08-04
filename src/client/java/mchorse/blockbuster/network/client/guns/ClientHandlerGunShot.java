package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.client.render.item.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.network.common.guns.PacketGunShot;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * GunShot client handler (P196). Legacy kicked the firing morph + shot-delay
 * timer on the cached gun ({@code models.get(stack).props.shot()}) for every
 * observing client. That client render cache + {@code GunProps.shot()} land in
 * P197 (SEAM); the entity lookup + stack fetch are ported so the handler is
 * total and ready to drive the cache when it arrives.
 */
public class ClientHandlerGunShot extends ClientMessageHandler<PacketGunShot>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunShot message)
    {
        Entity entity = player.getWorld().getEntityById(message.entity);

        if (entity instanceof LivingEntity)
        {
            LivingEntity base = (LivingEntity) entity;

            /* Kick the firing morph + shot-delay timer on the cached gun for
             * every observing client (P197). */
            TileEntityGunItemStackRenderer.shot(base.getMainHandStack());
        }
    }
}
