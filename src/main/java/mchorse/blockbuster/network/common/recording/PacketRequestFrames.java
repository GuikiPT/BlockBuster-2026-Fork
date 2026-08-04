package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Client → server request for an actor's frames, paired with the actor entity
 * {@code id} (roadmap P115). Server replies with {@link PacketRequestedFrames}
 * via {@code RecordUtils.sendRequestedRecord}.
 */
public class PacketRequestFrames implements IMessage
{
    public int id;
    public String filename;

    public PacketRequestFrames()
    {}

    public PacketRequestFrames(int id, String filename)
    {
        this.id = id;
        this.filename = filename;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
    }
}
