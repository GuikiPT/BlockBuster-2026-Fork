package mchorse.blockbuster.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for {@link Entity#playStepSound(BlockPos, BlockState)} (roadmap P53).
 *
 * <p>Replaces the legacy SRG-reflection {@code func_180429_a} lookup used by
 * {@code EntityMorph.playStepSound}; the yarn method is {@code protected} on
 * {@link Entity}.</p>
 */
@Mixin(Entity.class)
public interface EntityStepSoundAccessor
{
    @Invoker("playStepSound")
    void metamorph$invokePlayStepSound(BlockPos pos, BlockState state);
}
