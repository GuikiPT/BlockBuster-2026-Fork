package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.WorldEventListener;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the 1.12.2 ASM coremod's {@code WorldTransformer} (which injected
 * before {@code World.setBlockState}'s success return). Injected at HEAD so
 * the pre-change block entity is still there; the 3-arg overload delegates
 * here, so one injection covers both.
 */
@Mixin(World.class)
public abstract class WorldMixin
{
    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void blockbuster$onSetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir)
    {
        WorldEventListener.setBlockState((World) (Object) this, pos, state, flags);
    }
}
