package mchorse.metamorph.network.common;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * The morph blacklist, shipped whole to each client on login (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/PacketBlacklist.java</p>
 */
public class PacketBlacklist implements IMessage
{
    public Set<String> blacklist = new TreeSet<String>();

    public PacketBlacklist()
    {}

    public PacketBlacklist(Set<String> blacklist)
    {
        this.blacklist.addAll(blacklist);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            this.blacklist.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.blacklist.size());

        Iterator<String> it = this.blacklist.iterator();

        while (it.hasNext())
        {
            ForgeByteBufUtils.writeUTF8String(buf, it.next());
        }
    }
}
