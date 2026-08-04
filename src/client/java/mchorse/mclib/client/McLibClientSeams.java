package mchorse.mclib.client;

import mchorse.mclib.client.gui.framework.elements.input.TexturePickerBinder;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.config.Config;
import mchorse.mclib.network.mclib.client.ClientHandlerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * S22/P242 — the three McLib client seams that shipped with an inert default
 * and never got a production writer. One installer, one line at client init.
 *
 * <ul>
 * <li><b>{@link Icons#register()}</b> — legacy {@code mclib.CommonProxy.init()}
 * called it; the port had zero callers, so {@code IconRegistry} (the addon-facing
 * name&rarr;{@code Icon} map McLib exposes for Mappet-style UI APIs) was
 * permanently empty and every lookup-by-name answered {@code null}. Nothing
 * <i>inside</i> this port reads that map — it is a bundled-dependency API
 * surface, not an internal one — which is exactly why it stayed dark and why the
 * regression pin matters.</li>
 * <li><b>{@code GuiTexturePicker.textureBinder}</b> — see
 * {@link TexturePickerBinder}. Without it the picker accepted every selection
 * unvalidated and drew no preview, and the multi-skin editor measured every
 * child as 0&times;0 (so the canvas never sized itself and drew nothing).</li>
 * <li><b>{@code ClientHandlerConfig.storeServerConfig}</b> — legacy
 * {@code ClientHandlerConfig.run}'s {@code overwrite == false} branch, verbatim:
 * when the open screen is the mclib dashboard, hand the server's config module
 * to its config panel. An op pressing "download server config" got the packet
 * back and dropped it on the floor.</li>
 * </ul>
 */
public final class McLibClientSeams
{
    private McLibClientSeams()
    {}

    /**
     * Guards {@link Icons#register()} only: {@code IconRegistry.register}
     * answers a duplicate key by printing a stack trace and <b>keeping the old
     * value</b> (a legacy behaviour pinned by {@code IconsTest}), so a second
     * pass would be 80 harmless stack traces in the log. The other two seams are
     * plain assignments and are naturally idempotent.
     */
    private static boolean iconsRegistered;

    public static void install()
    {
        if (!iconsRegistered)
        {
            Icons.register();

            iconsRegistered = true;
        }

        TexturePickerBinder.install();

        ClientHandlerConfig.storeServerConfig = McLibClientSeams::storeServerConfig;
    }

    /**
     * Legacy body: {@code GuiScreen screen = mc.currentScreen; if (screen
     * instanceof GuiDashboard) ((GuiDashboard) screen).config.storeServerConfig(config)}.
     * Total — no client, no screen or a non-dashboard screen simply drops the
     * module, which is what legacy did too (no dashboard open ⇒ nowhere to put
     * it).
     */
    private static void storeServerConfig(Config config)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Screen screen = mc == null ? null : mc.currentScreen;

        if (config != null && screen instanceof GuiDashboard && ((GuiDashboard) screen).config != null)
        {
            ((GuiDashboard) screen).config.storeServerConfig(config);
        }
    }
}
