package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.common.guns.PacketGunProjectile;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * GunProjectile client handler (P196). Sets the projectile's morph to the one
 * carried by the packet (impact / restored morph). 1:1 port of 2.7.2's
 * {@code ClientHandlerGunProjectile}.
 */
public class ClientHandlerGunProjectile extends ClientMessageHandler<PacketGunProjectile>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunProjectile message)
    {
        Entity entity = player.getWorld().getEntityById(message.id);

        if (entity instanceof EntityGunProjectile)
        {
            ((EntityGunProjectile) entity).morph.set(message.morph);
        }
    }
}
