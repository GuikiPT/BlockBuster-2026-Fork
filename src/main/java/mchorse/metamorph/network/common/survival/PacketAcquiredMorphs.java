package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Full acquired-morph list (roadmap P55). Server→owner only: replaces the
 * client-side acquired list wholesale on login/spawn. Nulls (unreadable morphs)
 * are dropped on decode — total reader.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketAcquiredMorphs.java</p>
 */
public class PacketAcquiredMorphs implements IMessage
{
    public List<AbstractMorph> morphs;

    public PacketAcquiredMorphs()
    {
        this.morphs = new ArrayList<AbstractMorph>();
    }

    public PacketAcquiredMorphs(List<AbstractMorph> morphs)
    {
        this.morphs = morphs;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            AbstractMorph morph = MorphUtils.morphFromBuf(pbuf);

            if (morph != null)
            {
                this.morphs.add(morph);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        buf.writeInt(this.morphs.size());

        for (AbstractMorph morph : this.morphs)
        {
            MorphUtils.morphToBuf(pbuf, morph);
        }
    }
}
