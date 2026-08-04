package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

/**
 * Gun reload request (P196; legacy slot 45 S — {@code blockbuster:gun_reloading}).
 * The held gun stack + the player entity id. <b>Not OP-gated</b>. 1:1 port of
 * 2.7.2's {@code PacketGunReloading}.
 */
public class PacketGunReloading implements IMessage
{
    public ItemStack stack;
    public int id;

    public PacketGunReloading()
    {}

    public PacketGunReloading(ItemStack stack, int id)
    {
        this.stack = stack;
        this.id = id;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.stack = pbuf.readItemStack();
        this.id = pbuf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        pbuf.writeItemStack(this.stack == null ? ItemStack.EMPTY : this.stack);
        pbuf.writeInt(this.id);
    }
}
