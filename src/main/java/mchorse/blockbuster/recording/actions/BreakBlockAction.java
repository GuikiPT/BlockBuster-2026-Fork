package mchorse.blockbuster.recording.actions;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

/**
 * Breaking block action
 *
 * Actor breaks the block
 */
public class BreakBlockAction extends InteractBlockAction
{
    public boolean drop = false;

    public BreakBlockAction()
    {}

    public BreakBlockAction(BlockPos pos, boolean drop)
    {
        super(pos);
        this.drop = drop;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        actor.getWorld().breakBlock(this.pos, this.drop);
        actor.getWorld().setBlockBreakingInfo(actor.getId(), this.pos, -1);
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.drop = buf.readBoolean();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeBoolean(this.drop);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        this.drop = tag.getBoolean("Drop");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        tag.putBoolean("Drop", this.drop);
    }
}
