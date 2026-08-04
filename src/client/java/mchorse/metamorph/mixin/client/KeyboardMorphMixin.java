package mchorse.metamorph.mixin.client;

import mchorse.metamorph.client.KeyboardHandler;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raw keyboard hook for the global morph-keybind dispatch (roadmap P60).
 *
 * <p>Legacy Metamorph read the raw key straight off {@code Keyboard.getEventKey()}
 * inside its Forge {@code InputEvent.KeyInputEvent} handler. On Fabric the
 * equivalent seam is the tail of {@code Keyboard.onKey} (the same point BBS uses
 * for its animation-state key triggers): it fires after the game has already
 * routed the press through the keybind system, so a metamorph-bound key has
 * already been queued for {@code wasPressed()} and the handler's {@code wasUsed}
 * guard skips it. Only genuine key <b>presses</b> ({@code GLFW_PRESS}) are
 * forwarded; repeats and releases are ignored.</p>
 */
@Mixin(Keyboard.class)
public class KeyboardMorphMixin
{
    @Inject(method = "onKey", at = @At("TAIL"))
    private void metamorph$onEndKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo info)
    {
        if (action == GLFW.GLFW_PRESS)
        {
            KeyboardHandler.HANDLER.onRawKey(key, scancode);
        }
    }
}
