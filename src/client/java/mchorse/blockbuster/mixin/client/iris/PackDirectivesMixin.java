package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge;

/**
 * {@code shader_sun_path_rotation} (S21 P218).
 *
 * <p>This one curve never went through the uniform path even in 1.12.2:
 * {@code ShaderSunPathRotationCurve.apply} assigned
 * {@code Shaders.sunPathRotation} directly, and {@code reset()} restored the
 * value {@code AsmShaderHandler.afterInit()} had captured off the pack.</p>
 *
 * <p>The reason is the same on both platforms — the sun path rotation is not
 * (only) something the shader reads, it is something the <i>engine</i> reads to
 * place the celestial bodies and to build the shadow projection. On Iris that
 * is {@code PackDirectives.getSunPathRotation()}, consulted by
 * {@code CelestialUniforms}, {@code ShadowMatrices} and
 * {@code IrisRenderingPipeline}. Overriding the getter therefore moves the sun,
 * the shadows and the uniform together, which is what 1.12.2 did; overriding
 * only the GLSL uniform would move the shader's idea of the sun and leave the
 * shadow map behind.</p>
 *
 * <p>The first call also captures the pack's own value into
 * {@link ShaderCurveBridge#sunPathRotation()}, which is the restore target for
 * {@code reset()} — legacy's {@code afterInit()} capture, moved to the only
 * place the directive is authoritative.</p>
 *
 * <p>Note the double gate from legacy is preserved by the curve half:
 * {@code shader_sun_path_rotation} is registered only when a pack is loaded
 * <i>and</i> {@code optifine.shader_option_curve} is on, unlike the other
 * built-ins which need only the pack.</p>
 */
@Mixin(targets = "net.irisshaders.iris.shaderpack.properties.PackDirectives", remap = false)
public class PackDirectivesMixin
{
    @Inject(method = "getSunPathRotation", at = @At("RETURN"), cancellable = true, remap = false)
    private void blockbuster$overrideSunPathRotation(CallbackInfoReturnable<Float> info)
    {
        ShaderCurveBridge.sunPathRotation(info.getReturnValueF());

        Float override = ShaderCurveBridge.uniform1f.get("sunPathRotation");

        if (override != null)
        {
            info.setReturnValue(override);
        }
    }
}
