package mchorse.metamorph.network.common.creative;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.NBTUtils;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Morph payload primitive (roadmap P55).
 *
 * <p>Carries a single (nullable) {@link AbstractMorph} over the wire — the base
 * class every morph-bearing Metamorph packet extends. Decoding reads with the
 * S2 infinite-tag tracker ({@code NBTUtils.readInfiniteTag}) so oversized
 * Blockbuster morphs bypass the vanilla 2 MiB cap, exactly like legacy
 * {@code PacketMorph.fromBytes}; encoding delegates to the null-safe
 * {@link MorphUtils#morphToBuf(PacketByteBuf, AbstractMorph)} (P48).</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/creative/PacketMorph.java</p>
 */
public class PacketMorph implements IMessage
{
    public AbstractMorph morph;

    public PacketMorph()
    {}

    public PacketMorph(AbstractMorph morph)
    {
        this.morph = morph;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        /* Port note: legacy read the tag straight off the ByteBuf, but on
         * 1.20.4 {@link MorphUtils#morphToBuf} writes the tag via
         * {@code PacketByteBuf.writeNbt} (the name-less packet-NBT format), so
         * decoding must go through the matching {@code PacketByteBuf} reader —
         * {@code readInfiniteTag(PacketByteBuf)} — not the named disk reader. */
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.morph = MorphManager.INSTANCE.morphFromNBT(NBTUtils.readInfiniteTag(pbuf));
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        MorphUtils.morphToBuf(buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf), this.morph);
    }
}
