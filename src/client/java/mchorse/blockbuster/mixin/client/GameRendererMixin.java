package mchorse.blockbuster.mixin.client;

import mchorse.aperture.client.ApertureClient;
import mchorse.blockbuster.client.gui.GuiImmersiveEditor;
import mchorse.blockbuster.client.render.GunMiscRender;
import mchorse.blockbuster.client.video.CustomResolutionCapture;
import mchorse.blockbuster.client.video.VideoCaptureWiring;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S15 P178: the four GameRenderer seams (signatures verified via javap
 * against the loom-cache named jar):
 * <ul>
 * <li>{@code getFov(Camera, float, boolean)} RETURN override — playback /
 * editor-preview / smooth-camera FOV (legacy pinned
 * {@code gameSettings.fovSetting}); return-value override per the plan so
 * spyglass/panorama passes stay untouched when no context is active.</li>
 * <li>{@code tiltViewWhenHurt(MatrixStack, float)} HEAD — the only
 * injection point where roll composes correctly with hurt-tilt/bob
 * (technique ledger; vanilla has no roll). Roll is applied <b>only when
 * nonzero</b> (legacy branch); hurt tilt is suppressed while a camera
 * context is active.</li>
 * <li>{@code bobView(MatrixStack, float)} HEAD-cancel during a camera
 * context.</li>
 * <li>{@code renderHand(MatrixStack, Camera, float)} HEAD-cancel during
 * playback (legacy hid the hand in outside mode and playback).</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void blockbuster$onGetFov(CallbackInfoReturnable<Double> info)
    {
        Float fov = ApertureClient.getFovOverride();

        if (fov != null)
        {
            info.setReturnValue(fov.doubleValue());

            return;
        }

        /* Gun zoom FOV (P197): the legacy option mutation
         * (gameSettings.fovSetting = lastFov - lastFov*ZOOM_TIME*zoomFactor)
         * becomes a per-frame return-value scale, so a crash while zoomed can't
         * corrupt options.txt (behaviour-equal, implementation-different). Only
         * applies when the aperture camera isn't already overriding the FOV. */
        Double zoomed = GunMiscRender.fovOverride(info.getReturnValue());

        if (zoomed != null)
        {
            info.setReturnValue(zoomed);
        }

        /* Immersive editor (P143): legacy GuiImmersiveMorphMenu.onFovModifierEvent
         * ran at EventPriority.LOWEST, i.e. it had the final say over every other
         * FOV handler — hence this check comes last and overwrites whatever the
         * aperture/gun paths returned. GuiImmersiveEditor.current is the
         * MinecraftForge.EVENT_BUS.register(this.morphs) analog. */
        GuiImmersiveEditor editor = GuiImmersiveEditor.current;

        if (editor != null && editor.morphs != null && editor.morphs.shouldForceFov())
        {
            info.setReturnValue((double) editor.morphs.getForcedFov());
        }
    }

    /**
     * P143 immersive editor, render-tick START analog.
     *
     * <p>Legacy {@code GuiImmersiveMorphMenu.onRenderTick(Phase.START)} ran from
     * Forge's {@code RenderTickEvent}, which fires in {@code runGameLoop}
     * <b>before</b> {@code entityRenderer.updateCameraAndRender} — so the orbit
     * camera teleport and the preview-morph swap affected the very frame being
     * drawn. {@code WorldRenderEvents.START} is too late: it fires inside
     * {@code WorldRenderer#render}, after {@code GameRenderer#renderWorld} has
     * already called {@code camera.update(...)}, which made the orbit trail by
     * one frame (and, since {@code ImmersiveOrbitCamera.Result.applyTo} uses
     * {@code refreshPositionAndAngles}, without interpolation to hide it).
     * HEAD of {@code renderWorld} is the pre-camera position.</p>
     *
     * <p>S22 P270 shares this seam: {@code renderWorld} HEAD/RETURN is the exact
     * bracket a custom-resolution recording needs. The projection matrix is
     * built <i>inside</i> {@code renderWorld} (two {@code getBasicProjectionMatrix}
     * call sites, bytecode offsets 221 and 591) from
     * {@code Window.getFramebufferWidth()/getFramebufferHeight()}, so the swap and
     * the window-size override must both be live before it; and Fabric's
     * {@code WorldRenderEvents.LAST} — where both capture readbacks run (V-H
     * P258) — fires inside {@code WorldRenderer.render}, i.e. inside this
     * bracket, so the readback sees the capture framebuffer at the capture
     * size.</p>
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void blockbuster$onRenderWorldStart(CallbackInfo info)
    {
        CustomResolutionCapture.beginWorldRender();

        GuiImmersiveEditor editor = GuiImmersiveEditor.current;

        if (editor != null && editor.morphs != null)
        {
            editor.morphs.onRenderTickStart();
        }
    }

    /**
     * S22 P270: swap the client framebuffer back and turn the window-size
     * override off, so the HUD, screens and the on-screen blit that follow all
     * see the real window again.
     *
     * <p>A frame that throws never reaches this seam; {@code beginWorldRender}
     * therefore ends a leftover swap before starting a new one, which is the
     * try/finally equivalent a HEAD/RETURN injection pair cannot express.</p>
     */
    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void blockbuster$onRenderWorldEnd(CallbackInfo info)
    {
        /* S21 P272.1: under a shader pack this is where the framebuffer readback
         * happens — after WorldRenderer.render returned, i.e. after Iris'
         * finalizeLevelRendering composited into the main colour texture, and
         * still before InGameHud.render. A no-op without a pack, where the
         * readback stays on WorldRenderEvents.LAST. Must precede the swap-back
         * below. */
        VideoCaptureWiring.onWorldRenderEnd();

        CustomResolutionCapture.endWorldRender();
    }

    /**
     * P143 immersive editor, render-tick END analog — legacy's
     * {@code RenderTickEvent(Phase.END)} fired after the <b>whole</b> frame
     * (world + GUI), so the GUI pass still saw the preview morph. TAIL of
     * {@code GameRenderer#render} is that point.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void blockbuster$onRenderEnd(CallbackInfo info)
    {
        GuiImmersiveEditor editor = GuiImmersiveEditor.current;

        if (editor != null && editor.morphs != null)
        {
            editor.morphs.onRenderTickEnd();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo info)
    {
        float roll = ApertureClient.getRoll(tickDelta);
        boolean active = ApertureClient.isCameraActive();

        /* Immersive editor (P143): legacy onCameraOrient (EventPriority.LOWEST)
         * forced roll to 0 in immersion mode, overriding Aperture's own orient
         * handler — same last-word ordering here. */
        GuiImmersiveEditor editor = GuiImmersiveEditor.current;

        if (editor != null && editor.morphs != null && editor.morphs.shouldZeroRoll())
        {
            roll = 0;
        }

        if (roll != 0)
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));

            info.cancel();
        }
        else if (active)
        {
            /* Suppress hurt tilt during playback/preview */
            info.cancel();
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onBobView(MatrixStack matrices, float tickDelta, CallbackInfo info)
    {
        if (ApertureClient.isCameraActive())
        {
            info.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onRenderHand(CallbackInfo info)
    {
        if (ApertureClient.isCameraActive())
        {
            info.cancel();
        }
    }
}
