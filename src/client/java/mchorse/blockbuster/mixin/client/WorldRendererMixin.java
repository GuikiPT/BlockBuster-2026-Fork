package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.RenderingHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P203 — green-screen ("chroma") sky parity.
 *
 * <p>Faithful port of the 1.12.2 ASM patch
 * ({@code mchorse.blockbuster.core.transformers.RenderGlobalTransformer}), which
 * injected two prologues:</p>
 * <ul>
 *   <li>{@code RenderGlobal.renderSky(FI)V}:
 *       {@code if (isGreenSky()) { renderGreenSky(); return; }} — clear the colour
 *       buffer to the chroma colour and skip all vanilla sky geometry.</li>
 *   <li>{@code RenderGlobal.renderClouds(FIDDD)V}:
 *       {@code if (isGreenSky()) return;} — clouds are skipped entirely (a cancel,
 *       no substitute render).</li>
 * </ul>
 *
 * <p>The 1.20.4 seams (signatures verified via {@code javap} against the
 * loom-cache named jar) are {@code WorldRenderer.renderSky(MatrixStack, Matrix4f,
 * float, Camera, boolean, Runnable)} and {@code WorldRenderer.renderClouds(
 * MatrixStack, Matrix4f, float, double, double, double)} — both HEAD-cancellable.
 * The clear + fog-colour work lives in {@link RenderingHandler#renderGreenSky()}
 * (core-profile stand-in for the legacy {@code glDisable(GL_FOG)} is a shader fog
 * colour set to the chroma colour).</p>
 *
 * <p>Out of scope (S21): Sodium/Iris replace these methods, so the mixin may not
 * apply under a shader pack — log-and-continue, no chroma sky there.</p>
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Inject(
        method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blockbuster$onRenderSky(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci)
    {
        if (RenderingHandler.isGreenSky())
        {
            RenderingHandler.renderGreenSky();

            ci.cancel();
        }
    }

    @Inject(
        method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void blockbuster$onRenderClouds(MatrixStack matrices, Matrix4f projectionMatrix, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci)
    {
        if (RenderingHandler.isGreenSky())
        {
            ci.cancel();
        }
    }
}
