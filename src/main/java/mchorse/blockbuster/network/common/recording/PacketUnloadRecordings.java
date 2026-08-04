package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Clear the entire client record cache (roadmap P116) — 1:1 wire port of the
 * 2.7.2 packet. Empty body; sent on world change / unload.
 */
public class PacketUnloadRecordings implements IMessage
{
    public PacketUnloadRecordings()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
