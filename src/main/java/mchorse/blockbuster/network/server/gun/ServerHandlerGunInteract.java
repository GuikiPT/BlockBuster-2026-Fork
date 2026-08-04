package mchorse.blockbuster.network.server.gun;

import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.common.item.GunState;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInteract;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * GunInteract (shoot) server handler (P196). <b>No permission gate.</b> Gates
 * the shot on {@code state == READY_TO_SHOOT && (actor || storedShotDelay == 0)}
 * — actors bypass the shot-delay requirement (that's what lets recorded gun
 * playback fire at the recorded cadence); echoes {@link PacketGunInteract} back
 * to the sender (so their client runs {@code shootIt} for recoil/visuals)
 * <b>before</b> the server {@code shootIt}. 1:1 port of 2.7.2's
 * {@code ServerHandlerGunInteract}.
 */
public class ServerHandlerGunInteract extends ServerMessageHandler<PacketGunInteract>
{
    @Override
    public void run(ServerPlayerEntity player, PacketGunInteract packet)
    {
        interactWithGun(player, player.getWorld().getEntityById(packet.id), packet.stack);
    }

    public static void interactWithGun(ServerPlayerEntity player, Entity entity, ItemStack stack)
    {
        if (stack == null || !(stack.getItem() instanceof ItemGun))
        {
            return;
        }

        ItemGun gun = (ItemGun) stack.getItem();
        GunProps props = NBTUtils.getGunProps(stack);

        if (props == null)
        {
            return;
        }

        if (props.state == GunState.READY_TO_SHOOT && (entity instanceof EntityActor || props.storedShotDelay == 0))
        {
            if (player != null)
            {
                Dispatcher.sendTo(new PacketGunInteract(stack, entity == null ? 0 : entity.getId()), player);
            }

            /* Legacy: entity instanceof EntityPlayer ? (EntityPlayer) entity :
             * ((EntityActor) entity).fakePlayer. A shooter that is neither (or
             * an actor whose fake player failed to build) drops the shot
             * instead of legacy's ClassCastException. */
            PlayerEntity entityPlayer;

            if (entity instanceof PlayerEntity)
            {
                entityPlayer = (PlayerEntity) entity;
            }
            else if (entity instanceof EntityActor actor)
            {
                entityPlayer = actor.fakePlayer;
            }
            else
            {
                entityPlayer = null;
            }

            if (entityPlayer == null)
            {
                return;
            }

            gun.shootIt(stack, entityPlayer, entityPlayer.getWorld());
        }
    }
}
