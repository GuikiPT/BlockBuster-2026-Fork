package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.video.CustomResolutionCapture;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S18 P200 / S22 <b>P270</b> — the window-size override that makes recording at a
 * resolution other than the window's possible.
 *
 * <p>While {@link CustomResolutionCapture#isActive()} — i.e. <i>only</i> between
 * {@code GameRenderer.renderWorld}'s HEAD and RETURN, and only during a
 * custom-resolution recording — the window reports the capture size instead of
 * its own. That single lie is what makes the projection aspect ratio
 * ({@code GameRenderer.getBasicProjectionMatrix} divides
 * {@code getFramebufferWidth()} by {@code getFramebufferHeight()}), the
 * viewport, and every mod's world-render sizing agree with the capture
 * framebuffer the swap put in place. Without it the world renders with the
 * window's field of view stretched across a differently shaped frame.</p>
 *
 * <p>This is the <b>1.12.2 technique</b>, not a modern invention: Minema 3.7.1's
 * {@code DisplaySizeModifier} called {@code Minecraft.resize(frameWidth,
 * frameHeight)} on recording start, making {@code displayWidth}/
 * {@code displayHeight} the capture size for the whole session, and cancelled
 * real resizes while recording. The port's version is deliberately narrower —
 * scoped to the world render, so GUI scale, screen layout, mouse mapping and
 * resize handling never see it. See {@link CustomResolutionCapture} for the full
 * comparison and the blast-radius argument.</p>
 *
 * <p>Field and getter names verified with {@code javap} against the loom-cache
 * named jar (yarn 1.20.4+build.1): {@code Window} carries private
 * {@code width}/{@code height}/{@code framebufferWidth}/{@code framebufferHeight}/
 * {@code scaledWidth}/{@code scaledHeight}/{@code scaleFactor} behind six public
 * getters.</p>
 *
 * <p>{@code getWidth()}/{@code getHeight()} (logical window units) return the
 * same capture size as the framebuffer getters. On a HiDPI display those two
 * normally differ by the OS scale; the capture is defined in pixels, so the
 * ratio collapses for the duration of the world render. Nothing in vanilla's
 * world render reads them (the mouse mapping that does runs earlier in
 * {@code GameRenderer.render}), and treating a capture resolution as anything
 * but pixels would be the more surprising answer.</p>
 */
@Mixin(Window.class)
public class WindowMixin
{
    @Shadow
    private double scaleFactor;

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetWidth(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.width());
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetHeight(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.height());
        }
    }

    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetFramebufferWidth(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.width());
        }
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetFramebufferHeight(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.height());
        }
    }

    @Inject(method = "getScaledWidth", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetScaledWidth(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.scaled(CustomResolutionCapture.width(), this.scaleFactor));
        }
    }

    @Inject(method = "getScaledHeight", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onGetScaledHeight(CallbackInfoReturnable<Integer> info)
    {
        if (CustomResolutionCapture.isActive())
        {
            info.setReturnValue(CustomResolutionCapture.scaled(CustomResolutionCapture.height(), this.scaleFactor));
        }
    }
}
