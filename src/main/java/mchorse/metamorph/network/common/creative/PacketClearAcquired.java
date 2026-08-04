package mchorse.metamorph.network.common.creative;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Clear all acquired morphs request (roadmap P55). Empty payload — a pure
 * client→server signal.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/creative/PacketClearAcquired.java</p>
 */
public class PacketClearAcquired implements IMessage
{
    public PacketClearAcquired()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
