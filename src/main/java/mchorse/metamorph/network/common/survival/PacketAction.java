package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Morph action trigger (roadmap P55). Empty payload — a pure client→server
 * signal that the player pressed the morph-action key.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketAction.java</p>
 */
public class PacketAction implements IMessage
{
    public PacketAction()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
