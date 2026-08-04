package mchorse.blockbuster.network.common.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Client → server request for a record's action track (roadmap P117). The
 * server replies with {@link PacketActions}; {@code open} tells the client to
 * open the record in the editor on arrival.
 */
public class PacketRequestAction implements IMessage
{
    public String filename;
    public boolean open;

    public PacketRequestAction()
    {}

    public PacketRequestAction(String filename, boolean open)
    {
        this.filename = filename;
        this.open = open;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.open = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeBoolean(this.open);
    }
}
