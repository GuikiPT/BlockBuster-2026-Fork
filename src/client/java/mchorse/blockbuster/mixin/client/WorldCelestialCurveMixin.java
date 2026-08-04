package mchorse.blockbuster.mixin.client;

import mchorse.aperture.client.AsmRenderingHandler;
import mchorse.aperture.client.AsmRenderingHandler.Curve;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S15 P180 — {@code celestialangle} curve (sky-rotation override).
 *
 * <p>Replaces the 1.12.2 coremod redirect of {@code World.getCelestialAngle}.
 * On 1.20.4 the client-render sun/moon rotation reads
 * {@code World.getSkyAngleRadians(float)} (= celestial fraction {@code * 2*PI}),
 * so that is the render-safe seam. The legacy override was stored in degrees and
 * wrapped into {@code [0,360)} then divided by 360 — {@link AsmRenderingHandler#celestialFraction}
 * keeps that math verbatim; here the fraction is re-expanded into radians.</p>
 *
 * <p>Placed in the <b>client</b> mixin config so it only affects the physical
 * client's rendering — it never runs on a dedicated server, and on the
 * integrated server {@code getSkyAngleRadians} feeds only rendering, not the
 * time-of-day game logic (which reads the tick/day time directly). This is a
 * strictly-narrower effect than the legacy global coremod transform.</p>
 */
@Mixin(World.class)
public class WorldCelestialCurveMixin
{
    @Inject(method = "getSkyAngleRadians", at = @At("RETURN"), cancellable = true)
    private void blockbuster$onGetSkyAngleRadians(float tickDelta, CallbackInfoReturnable<Float> info)
    {
        Double value = AsmRenderingHandler.values.get(Curve.CelestialAngle);

        if (value != null)
        {
            float fraction = AsmRenderingHandler.celestialFraction(value);

            info.setReturnValue((float) (fraction * Math.PI * 2.0));
        }
    }
}
