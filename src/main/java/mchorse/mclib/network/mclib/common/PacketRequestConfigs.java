package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Full port of McLib 2.4.3's PacketRequestConfigs (roadmap P26): empty-body
 * op-gated "send me the server-side configs" request.
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketRequestConfigs.java</p>
 */
public class PacketRequestConfigs implements IMessage
{
    public PacketRequestConfigs()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
