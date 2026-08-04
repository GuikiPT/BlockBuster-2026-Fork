package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Snow walk ability (roadmap P49.1, registry id {@code snow_walk}).
 *
 * <p>This ability grants player snowy walk. This ability is really cool and
 * probably only will be used for snow man morph.</p>
 *
 * <p>Totally not taken from {@code EntitySnowman#onLivingUpdate()} (1.20.4:
 * {@code SnowGolemEntity#tickMovement}).</p>
 *
 * <p>API translation: 1.12 {@code Blocks.SNOW_LAYER} is 1.20.4
 * {@code Blocks.SNOW} (the full block is {@code SNOW_BLOCK}); the block
 * {@code Material.AIR} check → {@link BlockState#isAir()} (materials are gone in
 * 1.20.4); {@code Block.canPlaceBlockAt(world, pos)} →
 * {@link BlockState#canPlaceAt(net.minecraft.world.WorldView, BlockPos)}.</p>
 *
 * <p>Quirk preserved: legacy never gated on side, so the placement attempt also
 * runs client side (where {@code setBlockState} is a local, immediately
 * corrected guess). Ported verbatim.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/SnowWalk.java
 */
public class SnowWalk extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        if (!target.isOnGround())
        {
            return;
        }

        int i = MathHelper.floor(target.getX());
        int j = MathHelper.floor(target.getY());
        int k = MathHelper.floor(target.getZ());

        BlockPos blockpos = new BlockPos(i, j, k);
        BlockState snow = Blocks.SNOW.getDefaultState();

        if (target.getWorld().getBlockState(blockpos).isAir() && snow.canPlaceAt(target.getWorld(), blockpos))
        {
            target.getWorld().setBlockState(blockpos, snow);
        }
    }
}
