package mchorse.blockbuster.mixin.client;

import mchorse.aperture.camera.CameraOutside;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * S15 P177 / S22 P271 — the other half of legacy's
 * {@code onPreRenderPlayer}: your own nameplate stays off.
 *
 * <p>1.12.2's {@code RenderLivingBase.canRenderName} ends
 * {@code Minecraft.isGuiEnabled() && entity != this.renderManager
 * .renderViewEntity && flag && !entity.isBeingRidden()}, and it is called from
 * inside {@code RenderLivingBase.doRender} — i.e. <b>inside</b> the window in
 * which {@code CameraOutside.onPreRenderPlayer} had substituted
 * {@code renderViewEntity = mc.player}. So the one substitution legacy made
 * bought two things at once: the player was drawn, and the player's own name tag
 * was suppressed (because it now compared equal to the "view entity"). 1.12.2
 * outside mode with {@code hide_player} off showed a <i>bare</i> model.</p>
 *
 * <p>1.20.4 keeps the rule verbatim, one field renamed:
 * {@code LivingEntityRenderer.hasLabel} ends {@code MinecraftClient
 * .isHudEnabled() && livingEntity != minecraftClient.getCameraEntity() && bl
 * && !livingEntity.hasPassengers()} (javap, offsets 217-249 of the loom-cache
 * named jar — that {@code getCameraEntity} comparison is also why you never see
 * your own name in ordinary F5). Outside mode makes the camera entity the
 * detached {@code "Camera"} dummy, so without this the P271 fix would hang your
 * username over your model in every shot — a regression the fix itself would
 * have introduced.</p>
 *
 * <p>The redirect is legacy's substitution applied to the <i>read</i> instead of
 * the field: while the local player is being force-drawn, this one call answers
 * {@code mc.player}. Only the local player can be affected — for every other
 * entity {@code entity != mc.player} is true exactly as
 * {@code entity != dummy} was. {@code PlayerEntityRenderer} does not override
 * {@code hasLabel} (javap: only {@code renderLabelIfPresent}), so this single
 * seam covers the player. The call is the only {@code getCameraEntity()} in the
 * method, so no ordinal is needed.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererOutsideLabelMixin
{
    @Redirect(
        method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;")
    )
    private Entity blockbuster$labelViewer(MinecraftClient mc)
    {
        if (CameraOutside.showsLocalPlayer())
        {
            return mc.player;
        }

        return mc.getCameraEntity();
    }
}
