package mchorse.blockbuster.network.server.gun;

import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.network.common.guns.PacketZoomCommand;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * ZoomCommand server handler (P196). <b>No permission gate</b> (legacy hole,
 * flagged in the S19 permissions sweep): the stored zoom-on/zoom-off command
 * runs verbatim as the (possibly non-OP) player. Requires a mainhand gun and a
 * player target entity. 1:1 port of 2.7.2's {@code ServerHandlerZoomCommand}.
 */
public class ServerHandlerZoomCommand extends ServerMessageHandler<PacketZoomCommand>
{
    @Override
    public void run(ServerPlayerEntity player, PacketZoomCommand message)
    {
        if (!(player.getMainHandStack().getItem() instanceof ItemGun))
        {
            return;
        }

        Entity entity = player.getWorld().getEntityById(message.entity);
        GunProps props = NBTUtils.getGunProps(player.getMainHandStack());

        if (props == null || !(entity instanceof PlayerEntity))
        {
            return;
        }

        if (message.zoomOn)
        {
            ItemGun.commandSink.execute(player, props.zoomOnCommand);
        }
        else
        {
            ItemGun.commandSink.execute(player, props.zoomOffCommand);
        }
    }
}
