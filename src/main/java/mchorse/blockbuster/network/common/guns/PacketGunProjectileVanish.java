package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Projectile vanish notice (P196; legacy slot 47 C —
 * {@code blockbuster:gun_projectile_vanish}). Entity id + fade-out delay.
 * 1:1 port of 2.7.2's {@code PacketGunProjectileVanish}.
 */
public class PacketGunProjectileVanish implements IMessage
{
    public int id;
    public int delay;

    public PacketGunProjectileVanish()
    {}

    public PacketGunProjectileVanish(int id, int delay)
    {
        this.id = id;
        this.delay = delay;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.delay = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        buf.writeInt(this.delay);
    }
}
