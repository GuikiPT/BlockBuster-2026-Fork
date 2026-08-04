package mchorse.blockbuster.network.common.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client record listing for the {@code /action}/editor UI
 * (roadmap P117): int count + UTF8 record names.
 */
public class PacketActionList implements IMessage
{
    public List<String> records = new ArrayList<String>();

    public PacketActionList()
    {}

    public PacketActionList(List<String> records)
    {
        this.records.addAll(records);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            this.records.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.records.size());

        for (String record : this.records)
        {
            ForgeByteBufUtils.writeUTF8String(buf, record);
        }
    }
}
