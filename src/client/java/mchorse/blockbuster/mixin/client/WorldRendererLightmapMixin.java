package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.common.block.ChromaLight;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P97 — chroma block full-bright rendering.
 *
 * <p>Replaces 1.12.2's {@code BlockGreen.getPackedLightmapCoords} lightmap
 * override (hardcoded {@code 15728880}). On 1.20.4 there is no per-block
 * lightmap hook; {@code BlockModelRenderer} (both flat and smooth lighting
 * paths) reads the static
 * {@code WorldRenderer.getLightmapCoordinates(BlockRenderView, BlockState,
 * BlockPos)} — this HEAD injection short-circuits it for {@code green} chroma
 * states, returning {@link ChromaLight#FULLBRIGHT} so they render fully lit for
 * uniform chroma keying, <b>without</b> emitting extra light into the world
 * (the block still lights the world at level 1 via its {@code luminance}).</p>
 *
 * <p>{@code dim_green} is deliberately <b>not</b> intercepted
 * ({@link ChromaLight#isFullbright(BlockState)} returns false for it): it falls
 * through to the vanilla lightmap path, which reproduces the legacy
 * {@code BlockDimGreen} value (the real combined light). The legacy slab-below
 * fallback was a no-op on the chroma block's own state — see {@link ChromaLight}
 * — so nothing extra is wired for it.</p>
 */
@Mixin(WorldRenderer.class)
public class WorldRendererLightmapMixin
{
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void blockbuster$chromaFullbright(BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir)
    {
        if (ChromaLight.isFullbright(state))
        {
            cir.setReturnValue(ChromaLight.FULLBRIGHT);
        }
    }
}
