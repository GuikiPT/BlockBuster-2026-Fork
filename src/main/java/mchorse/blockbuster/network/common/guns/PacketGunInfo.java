package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Gun properties sync (P196; legacy slots 39 S / 41 C —
 * {@code blockbuster:gun_info}).
 *
 * <p>Carries the full {@code "Gun"} props compound plus the owning entity id.
 * The tag is read through {@link NBTUtils#readInfiniteTag(ByteBuf)} (the 1.12.2
 * {@code readInfiniteTag} size-limit bypass — gun props embed morphs and can
 * exceed the vanilla NBT cap; the S2 chunked transport carries oversized
 * payloads). Byte-for-byte 1:1 port of 2.7.2's {@code PacketGunInfo}.</p>
 */
public class PacketGunInfo implements IMessage
{
    public NbtCompound tag;
    public int entity;

    public PacketGunInfo()
    {
        this.tag = new NbtCompound();
    }

    public PacketGunInfo(NbtCompound tag, int entity)
    {
        this.tag = tag;
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.tag = NBTUtils.readInfiniteTag(pbuf);
        this.entity = pbuf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeTag(buf, this.tag);
        buf.writeInt(this.entity);
    }
}
