package mchorse.vanilla_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Shulker morph (roadmap P53.1).
 *
 * <p>Freezes the player at the block position where they morphed (silverfish-style
 * lock) and zeroes the inner entity's body yaw every tick so the shell always
 * faces north. The captured {@code BlockPos} round-trips as a {@code Pos}
 * int-array — the same key name BlockMorph uses (distinct classes, shared key).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/ShulkerMorph.java
 */
public class ShulkerMorph extends EntityMorph
{
    public BlockPos blockPos;

    @Override
    public void morph(LivingEntity target)
    {
        super.morph(target);

        this.blockPos = target.getBlockPos();
    }

    @Override
    public void demorph(LivingEntity target)
    {
        super.demorph(target);

        this.blockPos = null;
    }

    /**
     * Holds the player at the morphed block position and aligns the body north.
     */
    @Override
    public void update(LivingEntity target)
    {
        if (this.blockPos != null)
        {
            target.setVelocity(0, 0, 0);
            target.setPosition(this.blockPos.getX() + 0.5, this.blockPos.getY(), this.blockPos.getZ() + 0.5);
        }

        super.update(target);

        if (this.entity != null)
        {
            this.entity.setBodyYaw(0);
            this.entity.prevBodyYaw = 0;
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new ShulkerMorph();
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Pos"))
        {
            int[] pos = tag.getIntArray("Pos");

            if (pos.length == 3)
            {
                this.blockPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.blockPos != null)
        {
            tag.putIntArray("Pos", new int[] {this.blockPos.getX(), this.blockPos.getY(), this.blockPos.getZ()});
        }
    }
}
