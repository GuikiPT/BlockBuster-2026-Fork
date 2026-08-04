package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Base of the index-carrying survival packets (roadmap P55): a single {@code int}
 * index into the acquired-morph list.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketIndex.java</p>
 */
public abstract class PacketIndex implements IMessage
{
    public int index;

    public PacketIndex()
    {}

    public PacketIndex(int index)
    {
        this.index = index;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.index = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(index);
    }
}
