package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Frame;

import java.util.List;

/**
 * A single chunk of a chunked frame upload (roadmap P115). Prepends
 * {@code index}, {@code count}, {@code offset} ints <b>before</b> the base
 * {@link PacketFrames} fields — header order is wire-frozen.
 */
public class PacketFramesChunk extends PacketFrames
{
    public int index;
    public int count;
    public int offset;

    public PacketFramesChunk()
    {}

    public PacketFramesChunk(int index, int count, int offset, String filename, List<Frame> frames)
    {
        super(filename, 0, 0, frames);

        this.index = index;
        this.count = count;
        this.offset = offset;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.index = buf.readInt();
        this.count = buf.readInt();
        this.offset = buf.readInt();

        super.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.index);
        buf.writeInt(this.count);
        buf.writeInt(this.offset);

        super.toBytes(buf);
    }
}
