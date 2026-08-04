package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Client recorder lifecycle control (roadmap P116) — 1:1 wire port of the
 * 2.7.2 packet. Server → client: start/stop the local player's frame capture.
 */
public class PacketPlayerRecording implements IMessage
{
    public boolean recording;
    public String filename;
    public int offset;
    public boolean canceled;

    public PacketPlayerRecording()
    {}

    public PacketPlayerRecording(boolean recording, String filename, int offset, boolean canceled)
    {
        this.recording = recording;
        this.filename = filename;
        this.offset = offset;
        this.canceled = canceled;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.recording = buf.readBoolean();
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.offset = buf.readInt();
        this.canceled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.recording);
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeInt(this.offset);
        buf.writeBoolean(this.canceled);
    }
}
