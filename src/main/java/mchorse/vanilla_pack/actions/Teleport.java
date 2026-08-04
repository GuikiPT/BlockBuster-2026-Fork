package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Teleport action (roadmap P49.1, registry id {@code teleport}).
 *
 * <p>This action will teleport given player there where he's looking.</p>
 *
 * <p>If the point where player is about to teleport have a block above, it will
 * teleport player beside the block. Optionally, you can sneak to teleport beside
 * the block.</p>
 *
 * <p>Teleport action also has cooldown and limited distance to teleport in
 * radius of 32 blocks.</p>
 *
 * <p>API translation: {@code result.sideHit} → {@code getSide()};
 * {@code setPositionAndUpdate} → {@code requestTeleport};
 * {@code getBlock().equals(Blocks.AIR)} → {@code isAir()};
 * {@code EnumFacing.UP} offset → {@code BlockPos.up()}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Teleport.java
 */
public class Teleport implements IAction
{
    /**
     * The legacy destination math, extracted so it is testable headlessly
     * (roadmap P49.1 verification): the hit block, pushed one step along the hit
     * face when {@code offset}, then centred horizontally and raised one block.
     */
    public static Vec3d teleportTarget(BlockPos hit, Direction side, boolean offset)
    {
        BlockPos block = offset ? hit.offset(side) : hit;

        return new Vec3d(block.getX() + 0.5F, block.getY() + 1.0F, block.getZ() + 0.5F);
    }

    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        float reachDistance = 32;

        BlockHitResult result = EntityUtils.rayTrace(target, reachDistance, 1.0F);

        if (result != null && result.getType() == HitResult.Type.BLOCK)
        {
            BlockPos block = result.getBlockPos();

            if (target instanceof PlayerEntity && ((PlayerEntity) target).getAttackCooldownProgress(0.0F) < 1)
            {
                return;
            }

            boolean offset = target.isSneaking() || !target.getWorld().getBlockState(block.up()).isAir();
            Vec3d destination = teleportTarget(block, result.getSide(), offset);

            target.getWorld().playSound(null, target.prevX, target.prevY, target.prevZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
            target.requestTeleport(destination.x, destination.y, destination.z);

            if (target instanceof PlayerEntity)
            {
                ((PlayerEntity) target).resetLastAttackedTicks();
            }

            target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
        }
    }
}
