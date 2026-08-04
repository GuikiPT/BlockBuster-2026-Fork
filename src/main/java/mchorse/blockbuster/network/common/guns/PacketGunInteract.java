package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

/**
 * Gun-interact (shoot) request/echo (P196; legacy slots 44 S / 46 C —
 * {@code blockbuster:gun_interact}).
 *
 * <p>The held gun stack + the shooter/actor entity id. Client→server it is a
 * shoot request (gated in {@code ServerHandlerGunInteract#interactWithGun});
 * server→client it echoes back so the shooter's client runs {@code shootIt}
 * (recoil/visual half). The stack carries the full {@code "Gun"} tag, so this
 * packet routes through the S2 chunker when large.</p>
 *
 * <p>Legacy used Forge's {@code ByteBufUtils.readItemStack}/{@code writeItemStack};
 * yarn 1.20.4 maps to {@link PacketByteBuf#readItemStack()}/{@link
 * PacketByteBuf#writeItemStack(ItemStack)} (self-consistent round-trip).</p>
 */
public class PacketGunInteract implements IMessage
{
    public ItemStack stack;
    public int id;

    public PacketGunInteract()
    {}

    public PacketGunInteract(ItemStack stack, int id)
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
