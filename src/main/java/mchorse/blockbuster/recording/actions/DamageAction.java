package mchorse.blockbuster.recording.actions;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Damage action
 *
 * This action is responsible for dealing damage to the actor. Currently used
 * for killing the actor.
 */
public class DamageAction extends Action
{
    public float damage;

    public DamageAction()
    {}

    public DamageAction(float damage)
    {
        this.damage = damage;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        actor.damage(actor.getDamageSources().outOfWorld(), this.damage);
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.damage = buf.readFloat();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeFloat(this.damage);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.damage = tag.getFloat("Damage");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putFloat("Damage", this.damage);
    }
}
