package mchorse.blockbuster.network.common.structure;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Client -&gt; server structure-template request (roadmap P162; legacy slot 50 S
 * — {@code blockbuster:structure_request}).
 *
 * <p>1:1 wire port of 2.7.2's {@code PacketStructureRequest}: a single UTF8
 * {@code name}. An <b>empty</b> name means "send me ALL templates" — a
 * load-bearing sentinel used by {@code StructureMorph.request()} on first
 * render; a non-empty name requests one template (the UNLOADED-&gt;LOADING
 * dedupe path).</p>
 */
public class PacketStructureRequest implements IMessage
{
    public String name = "";

    public PacketStructureRequest()
    {}

    public PacketStructureRequest(String name)
    {
        this.name = name;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.name = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.name);
    }
}
