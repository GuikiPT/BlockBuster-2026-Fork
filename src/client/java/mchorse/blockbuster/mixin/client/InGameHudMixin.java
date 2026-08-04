package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.gui.GuiImmersiveEditor;
import mchorse.blockbuster.client.render.GunMiscRender;
import mchorse.metamorph.client.MetamorphHudWiring;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blockbuster HUD injections.
 *
 * <p>Gun crosshair suppression (P197): port of the legacy
 * {@code GunMiscRender.renderGameOverlay} HIGHEST-priority CROSSHAIRS cancel:
 * when the held gun hides its crosshair on zoom (and is zoomed) or supplies a
 * custom crosshair morph, the vanilla crosshair is suppressed. The custom
 * crosshair itself is drawn separately in the HUD render callback
 * ({@code GunMiscRender.drawCrosshair}). Signature verified via javap against the
 * loom-cache named jar: {@code InGameHud.renderCrosshair(DrawContext)}.</p>
 *
 * <p><b>Transparent-capture HUD hide (P203) — removed, S22 batch V-H.</b> This
 * class used to HEAD-cancel {@code InGameHud.render(DrawContext, float)} while
 * {@code RenderingHandler.isCaptureHidingHud()}, a flag nothing ever set. The
 * branch is gone rather than wired: both capture paths read the framebuffer on
 * {@code WorldRenderEvents.LAST}, which fires inside
 * {@code WorldRenderer.render} — i.e. before {@code GameRenderer.render} calls
 * {@code InGameHud.render} at all — so no HUD pixel can reach a captured frame,
 * and legacy's real HUD discipline (Aperture's editor-scoped
 * {@code gameSettings.hideGUI}) is ported as {@code options.hudHidden} in
 * {@code GuiCameraEditor}. See {@code CaptureHudDisciplineTest} and
 * {@code plan/inbox/batchV-H.md}.</p>
 *
 * <p>Metamorph squid-air takeover (P225): legacy cancelled the Forge
 * {@code RenderGameOverlayEvent.Pre(AIR)} element and drew its own 10-bubble bar
 * ({@code GuiHud.renderSquidAir}). 1.20.4 draws the air bubbles inline inside
 * the private {@code renderStatusBars}, so there is no element to cancel;
 * instead the two inputs of that segment are redirected. Vanilla's bubble
 * arithmetic and geometry are already bit-for-bit legacy's (max air 300, the
 * same {@code ceil} counts, drawn right-to-left from {@code width/2 + 91} on the
 * same status-bar row), so feeding it the morph's squid air reproduces the
 * legacy bar exactly — and suppressing the {@code isSubmergedIn} arm restores
 * legacy's "only when {@code squidAir < 300}" visibility rule. Both redirects
 * are inert whenever the takeover is off, which is every non-morphed frame.</p>
 */
@Mixin(InGameHud.class)
public class InGameHudMixin
{
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onRenderCrosshair(CallbackInfo info)
    {
        if (GunMiscRender.shouldCancelCrosshair())
        {
            info.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onRenderHud(DrawContext context, float tickDelta, CallbackInfo ci)
    {
        /* Immersive editor HUD suppression (P143): legacy
         * GuiImmersiveMorphMenu.onRenderGameOverlayEvent cancelled the overlay
         * unconditionally for the whole time the editor was open (NOT gated on
         * immersion mode) — hudSuppressed() is therefore always true while
         * GuiImmersiveEditor.current is set (the EVENT_BUS.register analog). */
        GuiImmersiveEditor editor = GuiImmersiveEditor.current;

        if (editor != null && editor.morphs != null && editor.morphs.hudSuppressed())
        {
            ci.cancel();
        }
    }

    @Redirect(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getAir()I"))
    private int blockbuster$squidAir(PlayerEntity player)
    {
        return MetamorphHudWiring.airBarValue(player.getAir());
    }

    @Redirect(method = "renderStatusBars", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSubmergedIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean blockbuster$squidAirSubmerged(PlayerEntity player, TagKey<Fluid> tag)
    {
        return MetamorphHudWiring.airBarSubmerged(player.isSubmergedIn(tag));
    }
}
