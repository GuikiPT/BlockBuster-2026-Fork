package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Server → client filename-only cache eviction (roadmap P115). The client
 * handler removes the one record from the client record map.
 */
public class PacketUnloadFrames implements IMessage
{
    public String filename;

    public PacketUnloadFrames()
    {}

    public PacketUnloadFrames(String filename)
    {
        this.filename = filename;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
    }
}
