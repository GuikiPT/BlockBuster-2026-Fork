package mchorse.blockbuster.mixin.client;

import mchorse.aperture.camera.CameraOutside;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * S15 P177 / S22 P252 — {@code outside → sky}.
 *
 * <p>Legacy's {@code Aperture.outsideSky} chose which entity
 * {@code EntityRenderer.updateFogColor} sampled the sky/fog colour at:
 * {@code CameraRunner.onRenderTick} set the render-view entity to
 * {@code outsideSky ? camera : mc.player} immediately before that method ran,
 * and {@code CameraOutside.onFogColor} — the {@code FogColors} handler posted at
 * the end of the very same method — put the camera back before the world was
 * drawn. One input, one call, then gone.</p>
 *
 * <p>On 1.20.4 that input is a <b>position</b>, not an entity:
 * {@code BackgroundRenderer.render} samples
 * {@code ClientWorld.getSkyColor(camera.getPos(), tickDelta)}, and
 * {@code camera.getPos()} is whatever the P178 {@code CameraMixin} last wrote —
 * the fixture position. Swapping {@code MinecraftClient.setCameraEntity} (what
 * the port did until P252) cannot move the sky here at all; what it does move is
 * which entity {@code WorldRenderer.render} skips, which is why the sky toggle
 * was silently deciding whether you could see yourself. So the option is applied
 * at the one call legacy's swap actually reached, and nowhere else — the sky
 * plane in {@code WorldRenderer.renderSky} keeps sampling at the camera, as it
 * did in 1.12.2 (by then legacy had already re-pinned the view entity).</p>
 *
 * <p>Target verified with javap against the loom-cache named jar:
 * {@code BackgroundRenderer.render(Camera, float, ClientWorld, int, float)}
 * (static) contains exactly one
 * {@code ClientWorld.getSkyColor(Vec3d, float)} call.
 * {@code CurveMixinSignatureTest} pins the signature.</p>
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererOutsideSkyMixin
{
    @ModifyArg(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;"),
        index = 0)
    private static Vec3d blockbuster$outsideSkyPosition(Vec3d cameraPos)
    {
        return CameraOutside.skyColorPosition(cameraPos);
    }
}
