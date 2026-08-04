package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Re-skin a record with the requester's own player data (roadmap P116) — 1:1
 * wire port of the 2.7.2 packet. Client → server (OP-only).
 */
public class PacketUpdatePlayerData implements IMessage
{
    public String record = "";

    public PacketUpdatePlayerData()
    {}

    public PacketUpdatePlayerData(String record)
    {
        this.record = record;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.record = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.record);
    }
}
