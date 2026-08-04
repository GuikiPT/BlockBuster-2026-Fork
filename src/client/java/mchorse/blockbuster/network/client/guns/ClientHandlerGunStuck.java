package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.common.guns.PacketGunStuck;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * GunStuck client handler (P196). Flags the projectile stuck and snaps its
 * position + lerp targets to the (float-precision) wire coords. 1:1 port of
 * 2.7.2's {@code ClientHandlerGunStuck}.
 */
public class ClientHandlerGunStuck extends ClientMessageHandler<PacketGunStuck>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunStuck packet)
    {
        Entity entity = player.getWorld().getEntityById(packet.id);

        if (entity instanceof EntityGunProjectile)
        {
            EntityGunProjectile bullet = (EntityGunProjectile) entity;

            bullet.stuck = true;
            bullet.targetX = packet.x;
            bullet.targetY = packet.y;
            bullet.targetZ = packet.z;
            bullet.setPosition(packet.x, packet.y, packet.z);
        }
    }
}
