package mchorse.blockbuster.network.common.structure;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Server -&gt; client structure push (roadmap P162; legacy slot 49 C —
 * {@code blockbuster:structure}).
 *
 * <p>1:1 wire port of 2.7.2's {@code network/common/structure/PacketStructure}:
 * a UTF8 {@code name} then the vanilla structure-template NBT. The tag is read
 * through {@link NBTUtils#readInfiniteTag(PacketByteBuf)} — 1.12.2 used
 * {@code readInfiniteTag} here (no size cap) because a baked template easily
 * exceeds the vanilla NBT limit; the S2 chunked transport carries the oversized
 * payload. A {@code null} tag is the load-bearing <b>deletion</b> notice the
 * server-tick hot-reload ({@code StructureMorph.checkStructures}) sends to make
 * clients drop the renderer and lazily re-request — {@link ForgeByteBufUtils#writeTag}
 * writes the null sentinel and {@code readInfiniteTag} restores {@code null}.</p>
 */
public class PacketStructure implements IMessage
{
    public String name = "";
    public NbtCompound tag;

    public PacketStructure()
    {}

    public PacketStructure(String name, NbtCompound tag)
    {
        this.name = name;
        this.tag = tag;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.name = ForgeByteBufUtils.readUTF8String(pbuf);
        this.tag = NBTUtils.readInfiniteTag(pbuf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.name);
        ForgeByteBufUtils.writeTag(buf, this.tag);
    }
}
