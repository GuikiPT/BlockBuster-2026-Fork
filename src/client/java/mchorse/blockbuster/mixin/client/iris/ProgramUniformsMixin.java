package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mchorse.blockbuster.client.compat.iris.GlUniformSink;
import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge;

/**
 * The built-in-uniform override seam (S21 P218).
 *
 * <p>Legacy owned this by ASM: {@code Shaders.setProgramUniform1f(su, value)}
 * was rewritten to call {@code AsmShaderHandler.setProgramUniform1f}, which
 * substituted the curve's value when {@code uniform1f} held one for that
 * uniform's name and otherwise passed Optifine's value through. Every built-in
 * shader curve — {@code shader_rain_strength} ({@code rainStrength}),
 * {@code shader_wetness}, {@code shader_frame_time} ({@code frameTimeCounter}),
 * {@code shader_world_time}, {@code shader_in_water} ({@code isEyeInWater}),
 * {@code shader_center_depth} ({@code centerDepthSmooth}) — rode that one
 * hook.</p>
 *
 * <p>Iris has no per-uniform setter to intercept; it batches its uniforms into
 * {@code ProgramUniforms} and pushes them from {@code update()} when a program
 * is bound. Injecting at {@code TAIL} of that method puts the override
 * <b>after</b> Iris has written every value it owns, for the program that is
 * bound right now, and before the draw call — which is the ordering the plan
 * calls out: run it earlier and Iris' own updater overwrites the curve on the
 * frames where both fire, and the built-ins flicker.</p>
 *
 * <p>The override is strictly opt-in per uniform: only names present in
 * {@code ShaderCurveBridge.uniform1f}/{@code uniform1i} are written, and the
 * curve classes remove their entry in {@code reset()} — which
 * {@code CurveManager.applyCurves} calls every frame for every channel that is
 * empty. An emptied channel therefore hands the uniform back to Iris on the
 * next frame with no further bookkeeping, exactly as in 1.12.2.</p>
 */
@Mixin(targets = "net.irisshaders.iris.gl.program.ProgramUniforms", remap = false)
public class ProgramUniformsMixin
{
    @Inject(method = "update", at = @At("TAIL"), remap = false)
    private void blockbuster$overrideCurveUniforms(CallbackInfo info)
    {
        GlUniformSink.install();
        ShaderCurveBridge.pushBuiltinOverrides();
    }
}
