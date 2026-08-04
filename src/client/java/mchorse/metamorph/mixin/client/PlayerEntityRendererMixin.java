package mchorse.metamorph.mixin.client;

import mchorse.aperture.camera.CameraOutside;
import mchorse.metamorph.client.render.MorphRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player morph render replacement (roadmap P54).
 *
 * <p>Modern replacement for the legacy {@code RenderSubPlayer} skinMap hack.
 * The morph render decision + transition animation live in {@link
 * MorphRenderer}; the mixin cancels the vanilla player render when the morph
 * takes over. Fires for <b>every</b> {@code AbstractClientPlayerEntity} (both
 * the local player and other players — legacy substituted both the {@code
 * default} and {@code slim} skinMap entries), never just the local player.</p>
 *
 * <p>The first-person arm injections mirror {@code RenderSubPlayer.renderLeft
 * /renderRightArm}: a morphed player whose morph claims the hand suppresses the
 * vanilla arm ({@code disable_first_person_hand} config honored in {@link
 * MorphRenderer#suppressVanillaHand}).</p>
 *
 * <p><b>Second tenant (S15 P177).</b> Aperture's {@code outside → hide_player}
 * needs exactly this capability — drop the local player's whole render — so it
 * rides this hook rather than installing a second cancel mechanism on the same
 * method. It is checked <i>before</i> the morph decision: a hidden player is
 * hidden whether or not it is morphed, which is what 1.12.2 did (the morph's
 * {@code RenderPlayerEvent} substitution never ran at all when the render-view
 * entity check dropped the player). See {@link CameraOutside#hidesPlayer}.</p>
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void metamorph$onRenderMorph(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        MorphRenderer.beginVanillaFade(MorphRenderer.NO_FADE);

        /* S15 P177: Aperture `outside → hide_player`. Cancelling from HEAD
         * skips the RETURN hook, and the fade is already disarmed above, so the
         * invariant the morph path relies on still holds. */
        if (CameraOutside.hidesPlayer(player))
        {
            ci.cancel();

            return;
        }

        if (MorphRenderer.renderPlayer(player, tickDelta, matrices, vertexConsumers, light))
        {
            ci.cancel();

            return;
        }

        /* Legacy's `else if (capability.isAnimating())` branch: the vanilla
         * player is the thing being drawn, so it gets the transition's alpha.
         * Cancelling above returns from HEAD, which skips the RETURN hook — so
         * the fade is armed only on the path that actually reaches vanilla. */
        MorphRenderer.beginVanillaFade(MorphRenderer.vanillaFade(player, tickDelta));
    }

    @Inject(
        method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("RETURN")
    )
    private void metamorph$endVanillaFade(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        MorphRenderer.endVanillaFade();
    }

    @Inject(
        method = "renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void metamorph$onRenderRightArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci)
    {
        if (MorphRenderer.suppressVanillaHand(player, Hand.MAIN_HAND, matrices, vertexConsumers, light, tickDelta()))
        {
            ci.cancel();
        }
    }

    @Inject(
        method = "renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void metamorph$onRenderLeftArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, CallbackInfo ci)
    {
        if (MorphRenderer.suppressVanillaHand(player, Hand.OFF_HAND, matrices, vertexConsumers, light, tickDelta()))
        {
            ci.cancel();
        }
    }

    /**
     * The arm hooks get no partial tick of their own — 1.12.2's
     * {@code renderHand} read it off the client the same way. A missing client
     * (headless) yields 0, which only affects animation phase.
     */
    private static float tickDelta()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null ? 0F : mc.getTickDelta();
    }
}
