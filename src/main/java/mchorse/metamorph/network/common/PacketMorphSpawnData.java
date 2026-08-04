package mchorse.metamorph.network.common;

import java.util.UUID;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.entity.EntityMorph;
import net.minecraft.network.PacketByteBuf;

/**
 * Morph ghost spawn data (roadmap P56.1 — port addition, no 1.12 slot;
 * client-bound {@code metamorph:morph_spawn_data}).
 *
 * <p>Forge 1.12.2 attached this payload to the vanilla spawn packet through
 * {@code IEntityAdditionalSpawnData}; yarn 1.20.4's
 * {@code EntitySpawnS2CPacket} has no NBT slot, so the server sends it to a
 * player that starts tracking a {@link mchorse.metamorph.entity.EntityMorph}.
 * The same approach {@code PacketActorSpawnData} and
 * {@code PacketGunProjectileSpawnData} take on the Blockbuster channel.</p>
 *
 * <p>The payload mirrors legacy {@code EntityMorph.writeSpawnData} field for
 * field: the owner UUID as a string ({@code ""} for none — legacy's own
 * sentinel) then the morph. A ghost's entire appearance <i>is</i> its morph, so
 * without this it renders as nothing.</p>
 */
public class PacketMorphSpawnData implements IMessage
{
    public int entity;
    public UUID owner;
    public AbstractMorph morph;

    public PacketMorphSpawnData()
    {}

    public PacketMorphSpawnData(int entity, UUID owner, AbstractMorph morph)
    {
        this.entity = entity;
        this.owner = owner;
        this.morph = morph;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.entity = pbuf.readInt();
        this.owner = EntityMorph.parseOwner(pbuf.readString());
        this.morph = MorphUtils.morphFromBuf(pbuf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        pbuf.writeInt(this.entity);
        pbuf.writeString(this.owner == null ? "" : this.owner.toString());
        MorphUtils.morphToBuf(pbuf, this.morph);
    }
}
