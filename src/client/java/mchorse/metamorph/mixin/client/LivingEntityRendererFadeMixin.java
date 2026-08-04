package mchorse.metamorph.mixin.client;

import mchorse.metamorph.client.render.MorphRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Demorph fade for the vanilla player model (roadmap P80.1).
 *
 * <p>Legacy {@code RenderingHandler.onPlayerRender} ended with
 * {@code GlStateManager.color(1, 1, 1, anim)} in the branch where the morph
 * render <i>declined</i> — the half of a 20-tick transition in which there is
 * no morph to draw and the vanilla player is what the frame shows. On 1.12.2
 * that was an ambient fixed-function vertex colour that the following
 * {@code renderModel} inherited. The 1.20.4 equivalent of that colour is the
 * four float arguments of {@code Model.render}, so the fade is applied by
 * rewriting the {@code alpha} one.</p>
 *
 * <p><b>Why the second injector exists.</b> A player model draws on
 * {@code EntityModel.getLayer(texture)} — an <i>alpha-tested cutout</i> layer,
 * which discards below 0.1 and is fully opaque above it. Writing an alpha into
 * it would produce a hard pop at 10%, not a fade. Vanilla's own translucent
 * player (the 0.15-alpha "invisible but visible to you" case) reaches a
 * blending layer by passing {@code translucent = true} into
 * {@code getRenderLayer}, so the fade borrows that exact switch. Verified with
 * {@code javap} against the loom-cache named jar: {@code getRenderLayer
 * (LivingEntity, boolean showBody, boolean translucent, boolean showOutline)}
 * and {@code EntityModel.render(MatrixStack, VertexConsumer, int, int, float,
 * float, float, float)} — one call site each inside
 * {@code LivingEntityRenderer.render}.</p>
 *
 * <p><b>The vanilla-alpha precedence is legacy's.</b> On 1.12.2 the ambient
 * colour was set in the {@code Pre} event and then <i>overwritten</i> by
 * {@code RenderLivingBase.renderModel}'s own {@code color(1, 1, 1, 0.15F)} for
 * a player who is invisible-but-visible-to-you. The same order is reproduced
 * here by leaving any alpha vanilla already chose ({@code != 1.0F}, which it
 * only is in that case) alone.</p>
 *
 * <p>Only Metamorph's own player-render hook ever arms
 * {@link MorphRenderer#beginVanillaFade}, and it disarms at RETURN, so every
 * other living entity drawn through this method sees {@code NO_FADE}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/RenderingHandler.java (onPlayerRender)
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererFadeMixin
{
    @ModifyArg(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/entity/LivingEntity;ZZZ)Lnet/minecraft/client/render/RenderLayer;"
        ),
        index = 2
    )
    private boolean metamorph$fadeNeedsBlending(boolean translucent)
    {
        return translucent || MorphRenderer.isVanillaFading();
    }

    @ModifyArg(
        method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
        ),
        index = 7
    )
    private float metamorph$fadeAlpha(float alpha)
    {
        return MorphRenderer.fadeAlphaOver(alpha);
    }
}
