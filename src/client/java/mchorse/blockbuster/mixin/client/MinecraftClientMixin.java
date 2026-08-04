package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.CameraHandlerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P44: the port of McLib's Forge {@code GuiOpenEvent} subscription — every
 * screen change (including {@code setScreen(null)}) runs the
 * {@code KeyboardHandler} GUI-scale override + session-static cleanup
 * state machine (see {@code mchorse.mclib.client.KeyboardHandler}).
 *
 * <p>P185.1 hangs Blockbuster's {@code CameraGUIHandler.onGuiOpen} analog off
 * the same HEAD injection, which is what gives it legacy's exact vantage
 * point: the incoming screen as the argument, the outgoing one still in
 * {@code currentScreen}.</p>
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin
{
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void blockbuster$onSetScreen(Screen screen, CallbackInfo ci)
    {
        if (BlockbusterClient.mclibKeys != null)
        {
            BlockbusterClient.mclibKeys.onScreenChange(screen);
        }

        CameraHandlerClient.onScreenChange(screen);
    }
}
