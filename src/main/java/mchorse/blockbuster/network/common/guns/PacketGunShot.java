package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Gun-shot broadcast (P196; legacy slot 42 C — {@code blockbuster:gun_shot}).
 *
 * <p>Just the shooter entity id; the observing client kicks the firing morph +
 * shot-delay timer on the cached gun. 1:1 port of 2.7.2's {@code PacketGunShot}.</p>
 */
public class PacketGunShot implements IMessage
{
    public int entity;

    public PacketGunShot()
    {}

    public PacketGunShot(int entity)
    {
        this.entity = entity;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.entity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entity);
    }
}
