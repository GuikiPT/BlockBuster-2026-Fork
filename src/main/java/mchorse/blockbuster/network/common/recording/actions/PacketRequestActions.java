package mchorse.blockbuster.network.common.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Empty-body request for the record list (roadmap P117). The server replies
 * with {@link PacketActionList}.
 */
public class PacketRequestActions implements IMessage
{
    public PacketRequestActions()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
