package mchorse.blockbuster.mixin.client;

import mchorse.aperture.client.RenderingHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P178.1 — chat suppression inside the camera editor.
 *
 * <p>Legacy {@code RenderingHandler.onChatDraw} cancelled Forge's
 * {@code RenderGameOverlayEvent.Chat} when {@code editorHideChat} was on and
 * the camera editor was the current screen. 1.20.4 has no such event; the
 * equivalent seam is a HEAD-cancel on {@code ChatHud.render}, whose signature
 * was verified with {@code javap} against the loom-cache named jar:
 * {@code render(Lnet/minecraft/client/gui/DrawContext;III)V}.</p>
 */
@Mixin(ChatHud.class)
public class ChatHudMixin
{
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;III)V", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onRenderChat(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci)
    {
        if (RenderingHandler.shouldHideChat())
        {
            ci.cancel();
        }
    }
}
