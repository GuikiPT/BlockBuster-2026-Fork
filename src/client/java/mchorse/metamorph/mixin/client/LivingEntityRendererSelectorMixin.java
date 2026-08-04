package mchorse.metamorph.mixin.client;

import mchorse.metamorph.client.EntityModelHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Selector render cancellation (roadmap P54.1).
 *
 * <p>Modern replacement for the legacy {@code RenderLivingEvent.Pre}
 * cancellation: when an {@link EntityModelHandler} selector matches this
 * living entity and its substituted morph renders, cancel the vanilla model
 * render. The re-entrancy guard lives in {@link
 * EntityModelHandler#renderEntity} ({@code currentRendering}) since selector
 * morphs may themselves be EntityMorphs that render living entities.</p>
 *
 * <p>Players are handled by the P54 player-morph render path, not here
 * ({@code PlayerEntityRenderer} overrides {@code render}).</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSelectorMixin
{
    @Inject(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void metamorph$onRenderSelector(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        EntityModelHandler handler = EntityModelHandler.INSTANCE;

        if (handler != null && handler.renderEntity(entity, 0.0D, 0.0D, 0.0D, matrices, vertexConsumers, light, tickDelta))
        {
            ci.cancel();
        }
    }
}
