package mchorse.blockbuster.network.server.gun;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInfo;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * GunInfo server handler (P196) — <b>OP-gated</b>. Saves the incoming props tag
 * onto the sender's mainhand stack, then echoes to the sender + all tracking
 * players. 1:1 port of 2.7.2's {@code ServerHandlerGunInfo}.
 */
public class ServerHandlerGunInfo extends ServerMessageHandler<PacketGunInfo>
{
    @Override
    public void run(ServerPlayerEntity player, PacketGunInfo message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        ItemStack stack = player.getMainHandStack();

        if (NBTUtils.saveGunProps(stack, message.tag))
        {
            IMessage packet = new PacketGunInfo(message.tag, player.getId());

            Dispatcher.sendTo(packet, player);
            Dispatcher.sendToTracked(player, packet);
        }
    }
}
