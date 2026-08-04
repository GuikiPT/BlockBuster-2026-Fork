package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Frame;

import java.util.List;

/**
 * Server → client requested frames (for an actor), with a trailing {@code id}
 * int — the actor entity id pairing from
 * {@code RecordUtils.sendRequestedRecord} (roadmap P115). {@code id} is
 * appended <b>after</b> the base frame payload.
 */
public class PacketRequestedFrames extends PacketFrames
{
    public int id;

    public PacketRequestedFrames()
    {}

    public PacketRequestedFrames(int id, String filename, int preDelay, int postDelay, List<Frame> frames)
    {
        super(filename, preDelay, postDelay, frames);

        this.id = id;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.id = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.id);
    }
}
