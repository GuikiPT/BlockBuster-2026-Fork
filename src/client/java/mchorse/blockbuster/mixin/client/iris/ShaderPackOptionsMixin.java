package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mchorse.blockbuster.client.compat.iris.IrisShaderOptions;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;

/**
 * Option discovery, at the only moment that is both late enough and early enough
 * (S22 P269 — the ordering half of S21 P218).
 *
 * <h2>Why this mixin exists</h2>
 *
 * <p>P218 discovered the pack's options at {@code Iris.loadShaderpack}
 * {@code @RETURN} ({@code IrisPackLifecycleMixin}). That is <b>too late</b>, and
 * the whole option-uniform half of P218 was inert because of it. The ordering
 * inside Iris 1.7.2, read off the bytecode of
 * {@code net.irisshaders.iris.shaderpack.ShaderPack.<init>}:</p>
 *
 * <pre>
 * Iris.loadShaderpack()
 *   └─ loadExternalShaderpack()
 *        └─ new ShaderPack(...)
 *             380  new IncludeGraph
 *             451  new ShaderPackOptions(graph, changedConfigs)   ← options exist here
 *            1227  new OptionMenuContainer(...)                   ← visibility exists here
 *            1387  new ProgramSet(base, sourceProvider, ...)
 *                    └─ readProgramSource → sourceProvider.apply
 *                         └─ JcppProcessor.glslPreprocessSource   ← the rewrite seam
 *   ← @RETURN of loadShaderpack (where discovery used to run)
 * </pre>
 *
 * <p>So {@code JcppProcessorMixin} fired <i>inside</i> the {@code ShaderPack}
 * constructor, with {@code ShaderCurveBridge.options}/{@code constOptions} still
 * empty — they had just been cleared at {@code loadShaderpack} {@code @HEAD}.
 * {@code ShaderCurveBridge.processSource} returns its input untouched in exactly
 * that state, so no source was ever rewritten, {@code addOptionUniform} was never
 * called, {@code option1f}/{@code option1i} stayed empty, and therefore
 * <b>every</b> downstream consumer saw nothing: no {@code _uniform_<OPTION>} in
 * the GLSL, no custom uniform registered by {@code CustomUniformsBuilderMixin},
 * and no {@code shader_<OPTION>} row from {@code describeOptions()}. The built-in
 * curves ({@code rainStrength}, {@code wetness}, {@code frameTimeCounter},
 * {@code isEyeInWater}, {@code sunPathRotation}) were unaffected — they ride
 * {@code ProgramUniformsMixin}/{@code PackDirectivesMixin} and need no discovery.
 *
 * <p>The defect was invisible until batch V-E: the six mixins had never applied
 * at all, so nothing downstream of them had ever run. This is the "consumer
 * exists, producer doesn't" pattern one level in — the producer existed, it just
 * ran after its consumer.</p>
 *
 * <h2>Why this seam</h2>
 *
 * <p>{@code ShaderPackOptions} is constructed in exactly <b>one</b> place in Iris
 * 1.7.2 — {@code ShaderPack.<init>}, verified by scanning every class in the jar
 * for the constructor reference — and at its {@code RETURN} its three fields
 * ({@code optionSet}, {@code optionValues}, {@code includes}) are all assigned.
 * That is after the pack's options are parsed and before the first program source
 * is read, i.e. legacy's position exactly: Optifine ran
 * {@code ShaderPackParser.collectShaderOptions} (Aperture's demotion hook) before
 * {@code Shaders.createVertShader} (Aperture's rewrite hook).</p>
 *
 * <p>A constructor {@code @RETURN} is used rather than an {@code ordinal}-keyed
 * injection into {@code ShaderPack.<init>}, for the reason
 * {@code IrisPackLifecycleMixin} already gives: an ordinal is the binding that
 * breaks on a patch bump.</p>
 *
 * <p>Display names and visibility are <b>not</b> available here — they come off
 * the pack's {@code LanguageMap} and {@code OptionMenuContainer}, and the pack is
 * still inside its own constructor. They are not needed for the rewrite (they
 * only decide the curve editor's row labels and its four registration groups), so
 * they are filled in afterwards by
 * {@link IrisShaderOptions#refineOptionMetadata()}, which
 * {@code IrisPackLifecycleMixin} calls at {@code loadShaderpack} {@code @RETURN}
 * — still before the curve refresh.</p>
 */
@Mixin(targets = "net.irisshaders.iris.shaderpack.option.ShaderPackOptions", remap = false)
public class ShaderPackOptionsMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void blockbuster$captureOptions(CallbackInfo info)
    {
        IrisShaderOptions.captureOptions((ShaderPackOptions) (Object) this);
    }
}
