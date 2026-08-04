package mchorse.blockbuster.network.server.gun;

import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.network.common.guns.PacketGunReloading;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * GunReloading server handler (P196). <b>No permission gate.</b> Reloads the
 * held mainhand gun. 1:1 port of 2.7.2's {@code ServerHandlerGunReloading}.
 */
public class ServerHandlerGunReloading extends ServerMessageHandler<PacketGunReloading>
{
    @Override
    public void run(ServerPlayerEntity player, PacketGunReloading packet)
    {
        ItemStack item = player.getMainHandStack();

        if (item.getItem() instanceof ItemGun)
        {
            ItemGun gun = (ItemGun) item.getItem();

            gun.reload(player, item);
        }
    }
}
