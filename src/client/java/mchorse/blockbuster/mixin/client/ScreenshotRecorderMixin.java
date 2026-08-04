package mchorse.blockbuster.mixin.client;

import mchorse.blockbuster.client.video.ScreenshotCapture;
import mchorse.blockbuster.client.video.ScreenshotConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * S22 <b>P298</b> — {@code screenshot.replace_vanilla}: vanilla F2 takes the
 * transparent screenshot.
 *
 * <p>No legacy counterpart: 1.12.2 Blockbuster shipped no screenshot code at all,
 * so this is the config surface of S18 P204's port addition, not a ported seam.
 * P204's own trigger is {@code key.blockbuster.screenshot_transparent}, which
 * defaults to {@code GLFW_KEY_UNKNOWN} — unbound — so without this the feature
 * had no reachable in-world trigger on a fresh install.</p>
 *
 * <p><b>Seam.</b> {@code ScreenshotRecorder.saveScreenshot(File, Framebuffer,
 * Consumer)} @HEAD, cancellable. Signature verified with {@code javap} against
 * the loom-cache named jar; the 3-arg overload is the one
 * {@code Keyboard.onKey}'s screenshot-key branch calls (also verified with
 * {@code javap -c}), and it is a static method, hence a static handler. Chosen
 * over hooking the key itself because it is the single funnel every vanilla
 * screenshot passes through, so the takeover cannot miss a rebound F2.</p>
 *
 * <p><b>It must be a strict no-op when off.</b> The entire decision is
 * {@link ScreenshotConfig#interceptsVanillaScreenshot(boolean, boolean)} — a pure
 * function, unit-tested there — and when it says no this method falls straight
 * through, so vanilla writes its ordinary opaque PNG into {@code screenshots/}
 * exactly as before. Default config says no ({@code replace_vanilla} is off), so
 * the shipped behaviour of F2 is unchanged.</p>
 *
 * <p><b>Why a queue rather than a grab.</b> Vanilla screenshots the framebuffer
 * as it stands, which at this point already has the HUD composited into it and an
 * opaque sky. The transparent still has to be grabbed inside the world render,
 * before the HUD — so the interception queues a one-frame request
 * ({@link ScreenshotCapture#requestWorldCapture}) that the owning readback seam
 * takes on the next frame, exactly like the keybind does. The one visible
 * consequence is a one-frame delay.</p>
 *
 * <p>Cancelling vanilla also cancels vanilla's "Saved screenshot as …" chat line,
 * so the request carries a callback that reports Blockbuster's own file through
 * the {@code Consumer<Text>} vanilla handed us — same clickable
 * {@code OPEN_FILE} style, fired from the IO worker after the PNG lands, which is
 * where vanilla calls its own message receiver from.</p>
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin
{
    @Inject(method = "saveScreenshot(Ljava/io/File;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), cancellable = true)
    private static void blockbuster$onSaveScreenshot(File directory, Framebuffer framebuffer,
        Consumer<Text> messageReceiver, CallbackInfo ci)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hasWorld = mc != null && mc.world != null;
        boolean screenOpen = mc == null || mc.currentScreen != null;

        if (!ScreenshotConfig.interceptsVanillaScreenshot(hasWorld, screenOpen))
        {
            return;
        }

        ScreenshotCapture.requestWorldCapture(file -> messageReceiver.accept(
            Text.translatable("blockbuster.screenshot.transparent_success", blockbuster$link(file))));

        ci.cancel();
    }

    /** Vanilla's own clickable-file styling for the chat report. */
    private static Text blockbuster$link(File file)
    {
        return Text.literal(file.getName()).styled((style) -> style
            .withFormatting(Formatting.UNDERLINE)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
    }
}
