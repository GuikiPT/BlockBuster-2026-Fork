package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Projectile stuck notice (P196; legacy slot 48 C —
 * {@code blockbuster:gun_stuck}).
 *
 * <p>Entity id + three <b>floats</b> {@code x/y/z} — the float width (not
 * double) is a load-bearing legacy format quirk: the client snaps position to
 * the float values, accepting the precision loss. 1:1 port of 2.7.2's
 * {@code PacketGunStuck}.</p>
 */
public class PacketGunStuck implements IMessage
{
    public int id;
    public float x;
    public float y;
    public float z;

    public PacketGunStuck()
    {}

    public PacketGunStuck(int id, float x, float y, float z)
    {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.x = buf.readFloat();
        this.y = buf.readFloat();
        this.z = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
        buf.writeFloat(this.z);
    }
}
