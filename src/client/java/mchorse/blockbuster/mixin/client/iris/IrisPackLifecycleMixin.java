package mchorse.blockbuster.mixin.client.iris;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mchorse.blockbuster.client.compat.iris.IrisShaderOptions;
import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge;

/**
 * Shader-pack (re)load lifecycle (S21 P218).
 *
 * <p>Legacy hooks: {@code AsmShaderHandler.loadShaderPack()} (called from
 * Optifine's {@code Shaders.loadShaderPack()}) cleared the source cache and the
 * discovered-option maps, and {@code afterInit()} (from {@code Shaders.init()})
 * captured {@code sunPathRotation} and reset the per-program uniform objects.
 * {@code CurveManager.refreshCurves()} then re-ran, which is how newly
 * discovered {@code shader_<OPTION>} ids appeared in the curve editor.</p>
 *
 * <p>{@code Iris.loadShaderpack()} is the modern equivalent and is the seam
 * chosen here rather than {@code loadExternalShaderpack} (BBS's choice)
 * because it is <b>public</b>, covers the fallback/internal-pack paths too, and
 * needs no {@code ordinal} on its {@code RETURN} — BBS's
 * {@code @At(value = "RETURN", ordinal = 9)} is exactly the kind of binding
 * that breaks on a patch bump.</p>
 *
 * <ul>
 *   <li>{@code HEAD} → {@link ShaderCurveBridge#onPackLoadStart()}: every cache
 *       dropped before the new pack is read.</li>
 *   <li>{@code RETURN} → {@link IrisShaderOptions#refineOptionMetadata()} (the
 *       discovered options' display names and visibility) and then
 *       {@link ShaderCurveBridge#onPackLoaded(String)}, which re-runs the curve
 *       refresh.</li>
 * </ul>
 *
 * <p><b>S22 P269 — discovery no longer happens here.</b> This {@code RETURN} used
 * to call {@code IrisShaderOptions.captureOptions()}, which was too late by a
 * whole {@code ShaderPack} constructor: Iris builds its {@code ProgramSet}s (and
 * therefore runs every source through {@code JcppProcessor}, i.e. through
 * {@code JcppProcessorMixin}) before {@code loadShaderpack} returns, so the
 * rewriter always saw an empty option set and the option half of P218 produced
 * nothing at all. Discovery moved to {@code ShaderPackOptionsMixin}; only the
 * metadata that genuinely does not exist until the pack is finished stays
 * here.</p>
 */
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public class IrisPackLifecycleMixin
{
    @Inject(method = "loadShaderpack", at = @At("HEAD"), remap = false)
    private static void blockbuster$beforePackLoad(CallbackInfo info)
    {
        ShaderCurveBridge.onPackLoadStart();
    }

    @Inject(method = "loadShaderpack", at = @At("RETURN"), remap = false)
    private static void blockbuster$afterPackLoad(CallbackInfo info)
    {
        IrisShaderOptions.refineOptionMetadata();
        ShaderCurveBridge.onPackLoaded(IrisShaderOptions.currentPackName());
    }

    /**
     * Shaders switched off entirely — drop the curve-written values too, or the
     * next pack's first frame inherits them.
     */
    @Inject(method = "setShadersDisabled", at = @At("HEAD"), remap = false)
    private static void blockbuster$onShadersDisabled(CallbackInfo info)
    {
        ShaderCurveBridge.reset();
    }
}
