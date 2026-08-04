package mchorse.blockbuster.network.client.guns;

import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.GunState;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.network.common.guns.PacketGunInteract;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * GunInteract client handler (P196) — the recoil/visual half. Mirrors the
 * server gate ({@code READY_TO_SHOOT && storedShotDelay == 0}) then runs
 * {@code gun.shootIt} locally on the shooter's client. 1:1 port of 2.7.2's
 * {@code ClientHandlerGunInteract}.
 */
public class ClientHandlerGunInteract extends ClientMessageHandler<PacketGunInteract>
{
    @Override
    public void run(ClientPlayerEntity player, PacketGunInteract packet)
    {
        if (packet.stack == null || !(packet.stack.getItem() instanceof ItemGun))
        {
            return;
        }

        ItemGun gun = (ItemGun) packet.stack.getItem();
        Entity entity = player.getWorld().getEntityById(packet.id);
        GunProps props = NBTUtils.getGunProps(packet.stack);

        if (entity instanceof PlayerEntity && props != null)
        {
            if (props.state == GunState.READY_TO_SHOOT && props.storedShotDelay == 0)
            {
                gun.shootIt(packet.stack, player, entity.getWorld());
            }
        }
    }
}
