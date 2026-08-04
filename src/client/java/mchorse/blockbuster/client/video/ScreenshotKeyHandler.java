package mchorse.blockbuster.client.video;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind + render wiring for the P204 world-variant transparent screenshot.
 *
 * <p>Self-contained (registers its own binding, tick poll and render hook) so it
 * does not depend on a sibling batch-4 phase owning the shared keybind family;
 * an integration agent may later fold the binding into
 * {@code mchorse.blockbuster.client.KeyboardHandler} if desired.</p>
 *
 * <ul>
 *   <li>Binding {@code key.blockbuster.screenshot_transparent} — default
 *       <b>unbound</b> ({@code GLFW_KEY_UNKNOWN}), under the existing
 *       {@code key.blockbuster.category}. Unbound by default so it never clashes
 *       with vanilla F2; users opt in. <b>S22 P298</b>: because it ships unbound,
 *       this was the whole feature's only in-world trigger and nobody had one —
 *       {@code screenshot.replace_vanilla} now offers F2 itself as an
 *       alternative, and the {@code screenshot} config category makes the
 *       feature visible in the settings screen at all.</li>
 *   <li>P298's feature switch ({@code screenshot.transparent}) is <b>not</b>
 *       checked here: it lives inside
 *       {@link ScreenshotCapture#requestWorldCapture()}, the one point every
 *       trigger funnels through, so the keybind and the F2 takeover cannot drift
 *       apart on it.</li>
 *   <li>The tick poll only drains presses while no screen is open (Fabric's
 *       {@code wasPressed()} queue is only fed then), so the world capture is an
 *       in-world action — the editor variant has its own trigger.</li>
 *   <li>The capture itself runs on {@code WorldRenderEvents.LAST} (after the
 *       world, before the HUD) so the HUD is never in the frame.</li>
 * </ul>
 */
public class ScreenshotKeyHandler
{
    private KeyBinding screenshot;
    private boolean registered;

    /** Register the binding + tick / render hooks (idempotent). */
    public void register()
    {
        if (this.registered)
        {
            return;
        }

        this.registered = true;

        this.screenshot = this.registerBinding(new KeyBinding(
            "key.blockbuster.screenshot_transparent",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.blockbuster.category"));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        /* WorldRenderEvents.LAST fires after the world render and before the
         * HUD — the "capture world without GUI" timing point (BBS's
         * onRenderBeforeScreen equivalent). */
        WorldRenderEvents.LAST.register(context ->
        {
            /* batch-4 integration: when P203's green_screen_sky force-alpha
             * seam lands, flip the per-frame alpha-sky flag before this grab
             * for full (not sky-only) transparency.
             *
             * S21 P272.1: the grab is gated on owning this frame's readback.
             * Without a shader pack that is always this seam (unchanged
             * behaviour); with one it is GameRenderer.renderWorld RETURN, driven
             * by VideoCaptureWiring.onWorldRenderEnd(). */
            ScreenshotCapture.captureWorldFrameAt(ShaderPackVideoCompat.Readback.WORLD_RENDER_LAST);
        });
    }

    /** Overridable so headless tests can skip the live {@code KeyBindingHelper}. */
    protected KeyBinding registerBinding(KeyBinding binding)
    {
        return KeyBindingHelper.registerKeyBinding(binding);
    }

    private void onClientTick(MinecraftClient mc)
    {
        if (this.screenshot == null)
        {
            return;
        }

        while (this.screenshot.wasPressed())
        {
            ScreenshotCapture.requestWorldCapture();
        }
    }
}
