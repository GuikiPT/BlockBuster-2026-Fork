package mchorse.blockbuster.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.metamorph.client.render.MorphRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P153 — snowstorm particle world-render hook.
 *
 * <p>Reproduces 1.12.2's ASM patch on {@code EntityRenderer.renderWorldPass}
 * (see legacy {@code EntityRendererTransformer}), which invoked
 * {@code RenderingHandler.renderParticles}/{@code renderLitParticles} immediately
 * after vanilla {@code ParticleManager.renderParticles}. On 1.20.4 the equivalent
 * seam is {@code WorldRenderer.render}, which calls
 * {@code ParticleManager.renderParticles(MatrixStack, VertexConsumerProvider$Immediate,
 * LightmapTextureManager, Camera, float)} once; this injects right after that call
 * so the Bedrock emitters draw in the same world pass, over the same depth buffer,
 * as vanilla particles.</p>
 *
 * <p>The {@code renderLitParticles} pair is kept for parity (the handler method is
 * an empty stub, reserved for morph-based rendering, exactly as in 1.12.2).</p>
 *
 * <h3>U-T — the model-view bracket (why this injector is not just a call)</h3>
 *
 * <p>1.12.2's ASM patch could get away with a bare call because the fixed-function
 * model-view matrix <i>was</i> the camera transform for the whole world pass:
 * {@code EntityRenderer.setupCameraTransform} set it once and everything drawn
 * afterwards inherited it. On 1.20.4 that is no longer true. During
 * {@code WorldRenderer.render} the global {@link RenderSystem#getModelViewStack()}
 * sits at its <b>base (identity)</b> state — geometry that goes through a
 * {@code VertexConsumer} is transformed on the CPU by the pass's own
 * {@link MatrixStack}, so nothing needs it — and each renderer that emits raw
 * camera-space vertices through {@code Tessellator} pushes the camera matrix for
 * itself. {@code ParticleManager.renderParticles} is one of those:</p>
 *
 * <pre>
 * MatrixStack stack = RenderSystem.getModelViewStack();
 * stack.push();
 * stack.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
 * RenderSystem.applyModelViewMatrix();
 *   ... build and draw every particle sheet ...
 * stack.pop();
 * RenderSystem.applyModelViewMatrix();
 * </pre>
 *
 * <p>The {@code pop()} is the last thing it does, and we inject <b>after</b> it —
 * so the Snowstorm billboards used to be drawn with an <b>identity</b> model-view:
 * no camera rotation at all, only {@code BedrockEmitter.setupOpenGL}'s
 * {@code -cameraPos} translate. The result is the live report "its location
 * projection in game is incorrect": the quads land at a spot that does not turn
 * with the view (their world-axis offset from the camera is read as view-space
 * X-right / Y-up / Z-back), and they vanish entirely whenever that offset points
 * behind the near plane. Geometry, colour, animation and depth are all untouched,
 * which is why every headless test and the dashboard preview stayed green — the
 * preview goes through {@code GuiModelRenderer}, which loads its own orbit matrix
 * into the very same stack, so it was never affected.</p>
 *
 * <p>So this injector re-establishes exactly the bracket vanilla just tore down,
 * with the same {@code matrices} vanilla used. Two ordering constraints:</p>
 *
 * <ul>
 *   <li>{@code BedrockEmitter.setupOpenGL} subtracts {@code Camera.getPos()}
 *       from the billboard <i>vertices</i> (P275; it used to translate this
 *       matrix instead, which broke vanilla's per-axis fog-distance
 *       decomposition and drew every quad in flat fog colour). Together with
 *       this bracket that is the full world→view transform, and it is exactly
 *       the shape vanilla particles are drawn in: camera-relative positions
 *       under a pure camera rotation.</li>
 *   <li>The morph path is the opposite case: {@code MorphRenderContext} hands the
 *       components the pass's {@link MatrixStack}, so their vertices are already
 *       CPU-transformed into camera space. Their flush ({@code consumers.draw()})
 *       must therefore happen <b>after</b> the pop, with the model-view back at
 *       identity — flushing inside the bracket would apply the camera rotation a
 *       second time.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public class WorldRendererParticlesMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;F)V",
            shift = At.Shift.AFTER
        )
    )
    private void blockbuster$renderSnowstormParticles(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f positionMatrix, CallbackInfo ci)
    {
        /* P230: the particle-morph appearance component draws a morph per
         * particle, which needs a MatrixStack + VertexConsumerProvider that the
         * legacy immediate-mode engine never had. That is exactly what
         * MorphRenderContext is for, so the owner of the frame — this pass —
         * pushes one. {@code matrices} here is the world pass's own stack (camera
         * rotation applied, camera translation not), which is why the component
         * subtracts the camera position itself, just as legacy did and just as
         * vanilla entity rendering does. The billboard-quad path is untouched: it
         * keeps its own RenderSystem model-view bracketing.
         *
         * The entity vertex consumers are the very Immediate the world pass hands
         * to ParticleManager; we flush it afterwards so the morph geometry is
         * drawn in this pass rather than whenever vanilla next flushes. */
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        MorphRenderContext.push(matrices, consumers, MorphRenderContext.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, tickDelta);

        /* U-T: restore the camera model-view ParticleManager.renderParticles just
         * popped, so the billboard pass's raw world-space quads are projected the
         * way 1.12.2's persistent fixed-function camera transform projected them.
         * Same stack, same matrix, same push/pop shape as vanilla's own bracket. */
        MatrixStack modelView = RenderSystem.getModelViewStack();

        modelView.push();
        modelView.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
        RenderSystem.applyModelViewMatrix();

        try
        {
            RenderingHandler.renderParticles(tickDelta);
            RenderingHandler.renderLitParticles(tickDelta);
        }
        finally
        {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();

            MorphRenderContext.pop();

            /* After the pop, on purpose: morph geometry is already CPU-transformed
             * into camera space by `matrices`, so it must flush under the identity
             * model-view vanilla flushes its own entity consumers under. */
            consumers.draw();
        }
    }
}
