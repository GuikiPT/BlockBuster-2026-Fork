package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.common.entity.EntityGunProjectile;
import mchorse.blockbuster.network.common.guns.PacketGunProjectileSpawnData;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Projectile spawn-data client handler (P195 port addition). Applies props +
 * morph + initial motion to a freshly-spawned projectile (the yarn replacement
 * for Forge's {@code readSpawnData}). See {@link PacketGunProjectileSpawnData}.
 */
public class ClientHandlerGunProjectileSpawnData extends ClientMessageHandler<PacketGunProjectileSpawnData>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunProjectileSpawnData message)
    {
        Entity entity = player.getWorld().getEntityById(message.entity);

        if (entity instanceof EntityGunProjectile)
        {
            ((EntityGunProjectile) entity).applySpawnData(message);
        }
    }
}
