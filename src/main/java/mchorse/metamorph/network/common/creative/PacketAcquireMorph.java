package mchorse.metamorph.network.common.creative;

import io.netty.buffer.ByteBuf;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Acquire morph packet (roadmap P55).
 *
 * <p>Extends {@link PacketMorph} with a {@code notify} flag: server→client it
 * tells the receiver to pop the acquired-morph GUI toast; client→server it is
 * the creative-menu "grab this morph" request.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/creative/PacketAcquireMorph.java</p>
 */
public class PacketAcquireMorph extends PacketMorph
{
    public boolean notify;

    public PacketAcquireMorph()
    {
        super();
    }

    public PacketAcquireMorph(AbstractMorph morph)
    {
        this(morph, true);
    }

    public PacketAcquireMorph(AbstractMorph morph, boolean notify)
    {
        super(morph);

        this.notify = notify;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.notify = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeBoolean(this.notify);
    }
}
