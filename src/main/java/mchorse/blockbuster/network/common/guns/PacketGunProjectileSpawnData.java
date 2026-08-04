package mchorse.blockbuster.network.common.guns;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Projectile spawn-data (P195 <b>port addition</b>; client-bound —
 * {@code blockbuster:gun_projectile_spawn_data}).
 *
 * <p>Forge 1.12.2 attached extra spawn data through
 * {@code IEntityAdditionalSpawnData}; yarn 1.20.4's {@code EntitySpawnS2CPacket}
 * carries no NBT, so the server sends this dedicated packet to tracking players
 * right after {@code spawnEntity}. The byte layout <b>mirrors the legacy
 * {@code EntityGunProjectile.writeSpawnData}</b> payload exactly (documented in
 * the S17 parity notes): an entity id, a bool-prefixed {@code props.toNBT()},
 * a bool-prefixed morph NBT, and three doubles {@code initMX/initMY/initMZ}.</p>
 *
 * <p>NBT compounds are read with {@link NBTUtils#readInfiniteTag(ByteBuf)}
 * (the size-limit bypass — props embed morphs), matching the legacy reader.</p>
 */
public class PacketGunProjectileSpawnData implements IMessage
{
    public int entity;
    public NbtCompound props;
    public NbtCompound morph;
    public double initMX;
    public double initMY;
    public double initMZ;

    public PacketGunProjectileSpawnData()
    {}

    public PacketGunProjectileSpawnData(int entity, NbtCompound props, NbtCompound morph, double initMX, double initMY, double initMZ)
    {
        this.entity = entity;
        this.props = props;
        this.morph = morph;
        this.initMX = initMX;
        this.initMY = initMY;
        this.initMZ = initMZ;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.entity = pbuf.readInt();

        if (pbuf.readBoolean())
        {
            this.props = NBTUtils.readInfiniteTag(pbuf);
        }

        if (pbuf.readBoolean())
        {
            this.morph = NBTUtils.readInfiniteTag(pbuf);
        }

        this.initMX = pbuf.readDouble();
        this.initMY = pbuf.readDouble();
        this.initMZ = pbuf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.entity);

        buf.writeBoolean(this.props != null);

        if (this.props != null)
        {
            ForgeByteBufUtils.writeTag(buf, this.props);
        }

        buf.writeBoolean(this.morph != null);

        if (this.morph != null)
        {
            ForgeByteBufUtils.writeTag(buf, this.morph);
        }

        buf.writeDouble(this.initMX);
        buf.writeDouble(this.initMY);
        buf.writeDouble(this.initMZ);
    }
}
