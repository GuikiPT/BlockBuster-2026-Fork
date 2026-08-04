package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

import java.util.Optional;

/**
 * Hand-rolled record request RPC (roadmap P116) — 1:1 wire port of the 2.7.2
 * packet. Client → server: request the named record; the {@code callbackID}
 * (-1 sentinel = none) pairs the server's {@code PacketFramesLoad} reply back
 * to a client-side consumer registered through
 * {@code ClientHandlerFramesLoad.registerConsumer} (P115).
 */
public class PacketRequestRecording implements IMessage
{
    private String filename = "";
    private int callbackID = -1;

    public PacketRequestRecording()
    {}

    public PacketRequestRecording(String record)
    {
        this(record, -1);
    }

    public PacketRequestRecording(String record, int callbackID)
    {
        this.filename = record;
        this.callbackID = callbackID;
    }

    public String getFilename()
    {
        return this.filename;
    }

    public Optional<Integer> getCallbackID()
    {
        return Optional.ofNullable(this.callbackID == -1 ? null : this.callbackID);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.callbackID = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeInt(this.callbackID);
    }
}
