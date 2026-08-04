package mchorse.metamorph.network.common.creative;

import io.netty.buffer.ByteBuf;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Sync one edited acquired morph back to the server (roadmap P55). Extends
 * {@link PacketMorph} with the {@code index} into the acquired-morph list.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/creative/PacketSyncMorph.java</p>
 */
public class PacketSyncMorph extends PacketMorph
{
    public int index;

    public PacketSyncMorph()
    {}

    public PacketSyncMorph(AbstractMorph morph, int index)
    {
        super(morph);
        this.index = index;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.index = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.index);
    }
}
