package mchorse.blockbuster.network.common.structure;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Server -&gt; client structure-name list (roadmap P162; legacy slot 51 C —
 * {@code blockbuster:structure_list}).
 *
 * <p>1:1 wire port of 2.7.2's {@code PacketStructureList}: an int count followed
 * by that many UTF8 names. Feeds the {@code blockbuster_structures} creative
 * picker category and registers empty placeholder renderers (see
 * {@code ClientHandlerStructureList}).</p>
 */
public class PacketStructureList implements IMessage
{
    public List<String> structures;

    public PacketStructureList()
    {
        this.structures = new ArrayList<String>();
    }

    public PacketStructureList(List<String> structures)
    {
        this.structures = structures;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.structures.clear();

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            this.structures.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.structures.size());

        for (String str : this.structures)
        {
            ForgeByteBufUtils.writeUTF8String(buf, str);
        }
    }
}
