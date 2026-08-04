package mchorse.blockbuster.mixin.client;

import mchorse.aperture.client.AsmRenderingHandler;
import mchorse.aperture.client.AsmRenderingHandler.Curve;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S15 P180 — vanilla sky/cloud colour curves.
 *
 * <p>Replaces the 1.12.2 Aperture coremod's {@code WorldTransformer} redirects
 * of {@code World.getSkyColor} / {@code World.getCloudColour}. The overrides are
 * driven from {@link AsmRenderingHandler#values} (populated by the
 * {@code skyr/skyg/skyb} and {@code cloudr/cloudg/cloudb} curves); when no curve
 * is active {@link AsmRenderingHandler#getOption} returns the vanilla component,
 * so this is a no-op outside camera playback / editor preview.</p>
 *
 * <p>Yarn signatures verified via javap against the loom-cache named jar:
 * {@code Vec3d getSkyColor(Vec3d, float)}, {@code Vec3d getCloudsColor(float)}.</p>
 */
@Mixin(ClientWorld.class)
public class ClientWorldCurveMixin
{
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void blockbuster$onGetSkyColor(CallbackInfoReturnable<Vec3d> info)
    {
        if (AsmRenderingHandler.values.isEmpty())
        {
            return;
        }

        Vec3d sky = info.getReturnValue();

        info.setReturnValue(new Vec3d(
            AsmRenderingHandler.getOption(Curve.SkyR, sky.x),
            AsmRenderingHandler.getOption(Curve.SkyG, sky.y),
            AsmRenderingHandler.getOption(Curve.SkyB, sky.z)));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void blockbuster$onGetCloudsColor(CallbackInfoReturnable<Vec3d> info)
    {
        if (AsmRenderingHandler.values.isEmpty())
        {
            return;
        }

        Vec3d cloud = info.getReturnValue();

        info.setReturnValue(new Vec3d(
            AsmRenderingHandler.getOption(Curve.CloudR, cloud.x),
            AsmRenderingHandler.getOption(Curve.CloudG, cloud.y),
            AsmRenderingHandler.getOption(Curve.CloudB, cloud.z)));
    }
}
