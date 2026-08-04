package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Projectile morph swap (P196; legacy slot 43 C —
 * {@code blockbuster:gun_projectile}).
 *
 * <p>Entity id + a morph (impact/restored morph). The morph rides the mclib
 * {@link MorphUtils#morphToBuf}/{@link MorphUtils#morphFromBuf} codec, which
 * itself reads with the unlimited tag-size tracker. 1:1 port of 2.7.2's
 * {@code PacketGunProjectile} (legacy's unused {@code MorphUtils} import for the
 * vanish variant is dropped, per the network-ledger note).</p>
 */
public class PacketGunProjectile implements IMessage
{
    public int id;
    public AbstractMorph morph;

    public PacketGunProjectile()
    {}

    public PacketGunProjectile(int id, AbstractMorph morph)
    {
        this.id = id;
        this.morph = morph;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.id = pbuf.readInt();
        this.morph = MorphUtils.morphFromBuf(pbuf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        pbuf.writeInt(this.id);
        MorphUtils.morphToBuf(pbuf, this.morph);
    }
}
