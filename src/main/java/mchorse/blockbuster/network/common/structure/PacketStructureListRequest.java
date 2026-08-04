package mchorse.blockbuster.network.common.structure;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Client -&gt; server request for the structure-name list (roadmap P162; legacy
 * slot 52 S — {@code blockbuster:structure_list_request}).
 *
 * <p>Empty payload — 1:1 with 2.7.2. Sent by {@code BlockbusterSection.update}
 * (P157) when the creative picker refreshes; the server replies with a
 * {@link PacketStructureList}.</p>
 */
public class PacketStructureListRequest implements IMessage
{
    public PacketStructureListRequest()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
