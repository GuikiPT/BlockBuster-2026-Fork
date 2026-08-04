package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mchorse.blockbuster.client.compat.iris.IrisShaderOptions;

/**
 * Registers one Iris custom uniform per discovered pack option, at
 * pipeline-build time (S21 P218).
 *
 * <p>This replaces legacy's {@code AsmShaderHandler.updateOptionUniforms()},
 * which Optifine called on every {@code Shaders.useProgram} and which pushed
 * {@code _uniform_<OPTION>} into whatever program had just been bound. Iris
 * already owns that machinery: a {@code CachedUniform} added to
 * {@code CustomUniforms} is assigned a location in every program that declares
 * it and pushed at its declared frequency. So the port hands Iris the uniforms
 * once, instead of chasing program binds.</p>
 *
 * <p>{@code PER_FRAME} is the right frequency — see
 * {@link IrisShaderOptions#addUniforms}. The values themselves come from
 * {@code ShaderCurveBridge}: the curve's value while a channel is live, the
 * pack's own configured value otherwise, which is precisely the two-step legacy
 * behaviour collapsed into one supplier.</p>
 *
 * <p>The injected uniforms are appended to the <b>already built</b>
 * {@code CustomUniforms}, so pack-authored custom uniforms keep their positions
 * and their dependency order; ours have no dependencies and can only ever be
 * last.</p>
 */
@Mixin(targets = "net.irisshaders.iris.uniforms.custom.CustomUniforms$Builder", remap = false)
public class CustomUniformsBuilderMixin
{
    @Inject(
        method = "build(Lnet/irisshaders/iris/uniforms/custom/CustomUniformFixedInputUniformsHolder;)Lnet/irisshaders/iris/uniforms/custom/CustomUniforms;",
        at = @At("RETURN"),
        remap = false
    )
    private void blockbuster$addOptionUniforms(CallbackInfoReturnable<Object> info)
    {
        if (info.getReturnValue() instanceof CustomUniformsAccessor accessor)
        {
            IrisShaderOptions.addUniforms(accessor.blockbuster$uniformOrder());
        }
    }
}
