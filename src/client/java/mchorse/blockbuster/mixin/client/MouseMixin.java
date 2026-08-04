package mchorse.blockbuster.mixin.client;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.blockbuster.client.render.GunMiscRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S15 P179: smooth (cinematic) camera input seam. While smooth mode is on
 * and no screen is open, vanilla's {@code updateMouse()} look-handling is
 * suppressed and the raw cursor deltas are fed into the
 * {@code CameraRenderer} accumulator instead (legacy read
 * {@code mouseHelper.deltaX/deltaY} per player tick; the sensitivity
 * scaling happens in {@code CameraRenderer.updateSmooth}). Signature
 * verified via javap: {@code updateMouse()V}, fields
 * {@code cursorDeltaX/cursorDeltaY}.
 *
 * <p><b>S22/P242</b> adds the second half of gun scoping: the look-sensitivity
 * override. {@code GunMiscRender.fovOverride} was already wired through
 * {@code GameRendererMixin}, but its sibling {@code sensitivityOverride} had
 * zero callers, so aiming down a gun's sights narrowed the FOV without slowing
 * the look — the two halves of legacy's zoom moved apart. Legacy mutated
 * {@code gameSettings.mouseSensitivity} (and restored it on release); the port
 * redirects the <i>read</i> instead, for the same reason P197 redirected the
 * FOV read: a crash while zoomed can no longer bake the zoomed value into
 * {@code options.txt}. The redirect sits on the single
 * {@code GameOptions.getMouseSensitivity().getValue()} call inside
 * {@code updateMouse} (javap-verified: {@code updateMouse} makes two
 * {@code SimpleOption.getValue()} calls — sensitivity at bytecode offset 54 and
 * {@code getInvertYMouse} at 274 — hence {@code ordinal = 0}), so vanilla's own
 * {@code v * 0.6 + 0.2} smoothing curve still applies on top — bit-for-bit what
 * legacy got.</p>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin
{
    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onUpdateMouse(CallbackInfo info)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (Aperture.smooth.get() && mc.currentScreen == null && mc.player != null)
        {
            ClientProxy.renderer.accumulateMouse(this.cursorDeltaX, this.cursorDeltaY);

            this.cursorDeltaX = 0;
            this.cursorDeltaY = 0;

            info.cancel();
        }
    }

    /**
     * P197 tail (S22/P242): gun-scope look sensitivity — legacy
     * {@code gameSettings.mouseSensitivity = lastMouseSensitivity * props.mouseZoom - 0.3f}
     * while zoomed, restored on release. Returning the override from the read
     * makes the restore implicit (there is no stored state to put back), so the
     * legacy {@code hasChangedSensitivity} latch has no counterpart here.
     */
    @Redirect(
        method = "updateMouse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;",
            ordinal = 0))
    private Object blockbuster$gunZoomSensitivity(SimpleOption<?> option)
    {
        Object value = option.getValue();

        if (value instanceof Double)
        {
            Double zoomed = GunMiscRender.sensitivityOverride((Double) value);

            if (zoomed != null)
            {
                return zoomed;
            }
        }

        return value;
    }
}
