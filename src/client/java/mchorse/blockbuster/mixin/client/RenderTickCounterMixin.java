package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.video.CaptureClock;
import mchorse.blockbuster.client.video.CaptureTiming;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P199 — Fixed-timestep playback clock.
 *
 * <p>While {@link CaptureClock#isActive()} (a recording is in progress), the
 * render clock is decoupled from wall time: every rendered frame advances game
 * time by exactly {@code 1/fps}, so playback is frame-perfect no matter how
 * fast or slow the encoder runs — Minema's "engine speed" built in. All timing
 * decisions come from the GL-free {@link CaptureTiming} core; this mixin is a
 * thin adapter that copies its result onto the shadowed vanilla fields and
 * returns the elapsed whole-tick count from {@code beginRenderTick}.</p>
 *
 * <p>When not recording the injection returns immediately, leaving vanilla
 * timing untouched — zero overhead and zero behavior drift for normal play.</p>
 *
 * <p>Field names verified against the yarn-1.20.4 loom-cache named jar
 * ({@code javap net.minecraft.client.render.RenderTickCounter}): {@code float
 * tickDelta}, {@code float lastFrameDuration}, {@code long prevTimeMillis},
 * {@code int beginRenderTick(long)}.</p>
 */
@Mixin(RenderTickCounter.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float tickDelta;

    @Shadow
    public float lastFrameDuration;

    @Shadow
    private long prevTimeMillis;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> info)
    {
        CaptureTiming.Decision decision = CaptureClock.tick();

        if (decision == null)
        {
            /* Not recording: leave vanilla wall-time behavior alone. */
            return;
        }

        /* Freeze wall time so vanilla never computes a catch-up tick burst
         * (neither this frame nor the first frame after recording stops). */
        this.prevTimeMillis = timeMillis;
        this.lastFrameDuration = CaptureClock.timing().getFrameDuration();
        this.tickDelta = decision.tickDelta;

        info.setReturnValue(decision.elapsedTicks);
    }
}
