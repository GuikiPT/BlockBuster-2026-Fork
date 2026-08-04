package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Full port of McLib 2.4.3's PacketDropItem (roadmap P26). Legacy quirk kept:
 * an empty stack writes <b>zero bytes</b> (no presence flag), and
 * {@code fromBytes} tolerates the resulting empty buffer via
 * {@code ForgeByteBufUtils.readTag}'s null-on-empty guard.
 *
 * <p>1.12.2 {@code stack.writeToNBT(new NBTTagCompound())} maps to yarn
 * {@code ItemStack.writeNbt(NbtCompound)}; {@code new ItemStack(tag)} maps to
 * static {@code ItemStack.fromNbt(NbtCompound)} (both verified via javap).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketDropItem.java</p>
 */
public class PacketDropItem implements IMessage
{
    public ItemStack stack = ItemStack.EMPTY;

    public PacketDropItem()
    {}

    public PacketDropItem(ItemStack stack)
    {
        this.stack = stack;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        NbtCompound tagCompound = ForgeByteBufUtils.readTag(buf);

        if (tagCompound != null)
        {
            this.stack = ItemStack.fromNbt(tagCompound);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        if (!this.stack.isEmpty())
        {
            ForgeByteBufUtils.writeTag(buf, this.stack.writeNbt(new NbtCompound()));
        }
    }
}
