package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.RenderingHandler;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S22 P231 — the record-synced GIF clock.
 *
 * <p>1.12.2 set {@code GifTexture.entityTick} from
 * {@code RenderingHandler.onPreRenderEntity(RenderLivingEvent.Pre)} and cleared
 * it in the {@code .Post} counterpart, so any animated GIF drawn inside a living
 * entity's render pass played against that entity's {@code ticksExisted} rather
 * than the global client tick. Fabric API has no {@code RenderLivingEvent}
 * equivalent on 1.20.4, so the pair is reproduced as HEAD/RETURN injections into
 * {@link LivingEntityRenderer#render}.</p>
 *
 * <p>{@code RETURN} (not {@code TAIL}) is used so the override is released on
 * every exit path. The pair is not re-entrant-safe by design — legacy's wasn't
 * either: a nested render restores {@code -1} rather than the outer entity's
 * tick. Nested living-entity renders do not happen on the vanilla path, and
 * morph rendering (which does nest) re-enters through the same seam with the
 * <i>same</i> record clock.</p>
 *
 * <p>Yarn signature verified via {@code javap} against the loom-cache named jar:
 * {@code void render(T, float, float, MatrixStack, VertexConsumerProvider, int)}
 * with {@code T} erased to {@code LivingEntity}.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererGifMixin
{
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void blockbuster$gifEntityTickPre(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        RenderingHandler.onPreRenderEntity(entity);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("RETURN"))
    private void blockbuster$gifEntityTickPost(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci)
    {
        RenderingHandler.onPostRenderEntity();
    }
}
