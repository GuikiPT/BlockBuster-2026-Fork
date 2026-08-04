package mchorse.blockbuster.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.aperture.client.AsmRenderingHandler;
import mchorse.aperture.client.AsmRenderingHandler.Curve;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15 P180 — vanilla fog colour + fog start/end curves.
 *
 * <p>Replaces the 1.12.2 coremod's {@code GlStateManagerTransformer} redirects of
 * {@code setFogColor}/{@code setFogStart}/{@code setFogEnd}. On 1.20.1 fog moved
 * out of the fixed-function {@code GlStateManager} into the core shader-fog
 * uniforms managed by {@link RenderSystem}: {@code BackgroundRenderer.setFogBlack}
 * pushes the colour and {@code BackgroundRenderer.applyFog} pushes start/end. We
 * override those uniforms right after vanilla sets them, sourcing values from the
 * {@code fogr/fogg/fogb} and {@code fogstart/fogend} curves.</p>
 *
 * <p>{@code setFogBlack} is a yarn misnomer, not a black-out: its whole body is
 * {@code RenderSystem.setShaderFogColor(red, green, blue)} over the fog colour
 * {@code render} just computed. 1.20.2 renamed it {@code applyFogColor} without
 * touching the body, so this is the same injection point on either version.</p>
 *
 * <p>Parity delta: the {@code fogdensity} curve has no 1.20.1 core equivalent
 * (fog is linear start/end now, not exponential density) — its id is still
 * registered so profiles round-trip, but it is not applied to the render. See
 * the stage parity notes.</p>
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererCurveMixin
{
    @Inject(method = "setFogBlack", at = @At("TAIL"))
    private static void blockbuster$onApplyFogColor(CallbackInfo info)
    {
        if (AsmRenderingHandler.values.isEmpty())
        {
            return;
        }

        float[] color = RenderSystem.getShaderFogColor();

        RenderSystem.setShaderFogColor(
            (float) AsmRenderingHandler.getOption(Curve.FogR, color[0]),
            (float) AsmRenderingHandler.getOption(Curve.FogG, color[1]),
            (float) AsmRenderingHandler.getOption(Curve.FogB, color[2]),
            color[3]);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void blockbuster$onApplyFog(CallbackInfo info)
    {
        if (AsmRenderingHandler.values.isEmpty())
        {
            return;
        }

        RenderSystem.setShaderFogStart((float) AsmRenderingHandler.getOption(Curve.FogStart, RenderSystem.getShaderFogStart()));
        RenderSystem.setShaderFogEnd((float) AsmRenderingHandler.getOption(Curve.FogEnd, RenderSystem.getShaderFogEnd()));
    }
}
