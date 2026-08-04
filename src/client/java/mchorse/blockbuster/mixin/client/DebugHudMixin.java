package mchorse.blockbuster.mixin.client;

import mchorse.aperture.client.RenderingHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * P178.1 — the F3 debug-overlay half of Aperture's legacy
 * {@code RenderingHandler.onHUDRender(RenderGameOverlayEvent.Text)}:
 *
 * <ul>
 * <li>the whole debug text block is cancelled while the camera editor is the
 * current screen (legacy {@code event.setCanceled(true)}, ungated by any
 * config);</li>
 * <li>{@code "Camera ticks N"} is appended to the left column while the camera
 * runner is playing.</li>
 * </ul>
 *
 * <p>Signatures verified with {@code javap} against the loom-cache named jar:
 * {@code DebugHud.render(Lnet/minecraft/client/gui/DrawContext;)V} and
 * {@code DebugHud.getLeftText()Ljava/util/List;}.</p>
 */
@Mixin(DebugHud.class)
public class DebugHudMixin
{
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void blockbuster$onRenderDebugHud(DrawContext context, CallbackInfo ci)
    {
        if (RenderingHandler.shouldHideDebugHud())
        {
            ci.cancel();
        }
    }

    @Inject(method = "getLeftText", at = @At("RETURN"))
    private void blockbuster$onGetLeftText(CallbackInfoReturnable<List<String>> cir)
    {
        String line = RenderingHandler.debugTickLine();

        if (line == null)
        {
            return;
        }

        List<String> list = cir.getReturnValue();

        if (list == null)
        {
            return;
        }

        /* Totality: some other mod may have swapped in an immutable list. */
        try
        {
            list.add(line);
        }
        catch (UnsupportedOperationException e)
        {
            /* nothing we can do — the debug line is cosmetic */
        }
    }
}
