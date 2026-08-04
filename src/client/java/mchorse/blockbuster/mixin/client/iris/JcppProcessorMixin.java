package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge;

/**
 * <b>The</b> source seam (S21 P218): rewrite pack GLSL on its way into the
 * preprocessor.
 *
 * <p>Legacy intercepted Optifine's {@code Shaders.createVertShader} /
 * {@code createGeomShader} / {@code createFragShader} and swapped the
 * {@code BufferedReader} they were about to compile
 * ({@code AsmShaderHandler.getCachedShader}). Iris' single equivalent choke
 * point is {@code JcppProcessor.glslPreprocessSource(String, Iterable)}: every
 * program's include-resolved source passes through it exactly once before jcpp
 * evaluates the preprocessor directives, which is the same position in the
 * pipeline legacy occupied.</p>
 *
 * <p>Being <i>before</i> jcpp is what makes the eligibility rules load-bearing
 * rather than cosmetic: after this mixin runs, an option's {@code #define} no
 * longer expands to a literal, so any option the preprocessor still needs to
 * evaluate ({@code #if}, {@code case}, array size) must already have been
 * demoted to {@code NOT_SUPPORT} — which is exactly what the demotion pass over
 * Iris' include graph does at pack-load time.</p>
 *
 * <p>{@code remap = false} throughout: Iris is a mod, not Minecraft, so its
 * names are not in the yarn refmap.</p>
 *
 * @see mchorse.blockbuster.client.compat.iris.ShaderSourceRewriter
 */
@Mixin(targets = "net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor", remap = false)
public class JcppProcessorMixin
{
    @ModifyVariable(method = "glslPreprocessSource", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false)
    private static String blockbuster$rewriteOptionUniforms(String source)
    {
        return ShaderCurveBridge.processSource(source);
    }
}
