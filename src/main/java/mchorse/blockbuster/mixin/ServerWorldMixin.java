package mchorse.blockbuster.mixin;

import mchorse.blockbuster.recording.capturing.WorldEventListener;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the two server-side halves of 1.12.2's
 * {@code IWorldEventListener}:
 *
 * <ul>
 * <li>{@code sendBlockBreakProgress} (block-break animation capture during
 * recording) — HEAD of {@code ServerWorld.setBlockBreakingInfo}, the
 * server-side entry point vanilla calls when a player's mining progress
 * changes;</li>
 * <li>{@code notifyBlockUpdate} (the P113 damage-control block feed) — HEAD of
 * {@code ServerWorld.onBlockChanged}, which vanilla's
 * {@code World.setBlockState} invokes once on the success path with the
 * pre-change state, i.e. the same seam 1.12.2's {@code markAndNotifyBlock}
 * used. Targeting the {@code ServerWorld} override (rather than
 * {@code World}'s) keeps the injection server-only, mirroring legacy's
 * {@code !world.isRemote} listener attachment.</li>
 * </ul>
 *
 * <p>Both signatures verified with {@code javap} against the loom-named
 * 1.20.4 jar.</p>
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin
{
    @Inject(method = "setBlockBreakingInfo(ILnet/minecraft/util/math/BlockPos;I)V", at = @At("HEAD"))
    private void blockbuster$onSetBlockBreakingInfo(int entityId, BlockPos pos, int progress, CallbackInfo info)
    {
        WorldEventListener.sendBlockBreakProgress((World) (Object) this, entityId, pos, progress);
    }

    @Inject(method = "onBlockChanged(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)V", at = @At("HEAD"))
    private void blockbuster$onBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo info)
    {
        WorldEventListener.notifyBlockUpdate((World) (Object) this, pos, oldBlock, newBlock, 0);
    }
}
