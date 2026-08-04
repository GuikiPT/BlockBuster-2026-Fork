package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.common.guns.PacketGunProjectileVanish;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * GunProjectileVanish client handler (P196). Flags the projectile vanishing +
 * sets its fade-out delay. 1:1 port of 2.7.2's
 * {@code ClientHandlerGunProjectileVanish}.
 */
public class ClientHandlerGunProjectileVanish extends ClientMessageHandler<PacketGunProjectileVanish>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunProjectileVanish message)
    {
        Entity entity = player.getWorld().getEntityById(message.id);

        if (entity instanceof EntityGunProjectile)
        {
            EntityGunProjectile projectile = (EntityGunProjectile) entity;

            projectile.vanish = true;
            projectile.vanishDelay = message.delay;
        }
    }
}
