package mchorse.metamorph.mixin.client;

import mchorse.metamorph.client.render.EntityMorphRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cancel a morph dummy's own nameplate (roadmap P80.1).
 *
 * <p>Legacy's {@code RenderingHandler.onNameRender} opened with an
 * <b>unconditional</b> {@code event.setCanceled(true)} whenever {@code
 * EntityMorph.renderEntity} was set. That is not decoration: {@code
 * EntityMorph.setEntity} calls {@code setAlwaysRenderNameTag(true)} on the
 * dummy (ported verbatim as {@code setCustomNameVisible(true)}), so without the
 * cancel every mob disguise would advertise the mob's name — "Zombie", "Cow" —
 * over its wearer's head. The two halves are a pair; neither makes sense alone.</p>
 *
 * <p><b>Why the redirect sits on {@code EntityRenderer.render} rather than on
 * {@code hasLabel}.</b> {@code hasLabel} is overridden all over the renderer
 * tree, and not every override chains to {@code super} — {@code
 * ArmorStandEntityRenderer}'s reimplements the whole distance/team body inline.
 * Cancelling at any one override would leave those renderers drawing the label
 * anyway. The single {@code hasLabel} call site inside {@code
 * EntityRenderer.render} is the choke point every entity draw passes through,
 * and it is the exact place legacy's {@code RenderLivingEvent.Specials.Pre}
 * fired. Verified with {@code javap} against the loom-cache named jar.</p>
 *
 * <p>This only ever <b>removes</b> a label; {@link
 * mchorse.metamorph.client.render.MorphNameplate} is what puts the host's name
 * back, and only for a host that passes legacy's own visibility rules.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/RenderingHandler.java (onNameRender)
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameMixin
{
    @Shadow
    protected abstract boolean hasLabel(Entity entity);

    @Redirect(
        method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;hasLabel(Lnet/minecraft/entity/Entity;)Z"
        )
    )
    private boolean metamorph$hideMorphDummyLabel(EntityRenderer<Entity> self, Entity entity)
    {
        if (EntityMorphRenderer.renderEntity != null)
        {
            return false;
        }

        return this.hasLabel(entity);
    }
}
