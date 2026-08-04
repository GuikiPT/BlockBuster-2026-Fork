package mchorse.mclib.client;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.utils.GuiInventoryElement;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.config.gui.ConfigGuiProviders;
import mchorse.mclib.events.RemoveDashboardPanels;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Port of McLib 2.4.3 {@code mchorse.mclib.client.KeyboardHandler}
 * (roadmap P44).
 *
 * <p>Dashboard keybind exactly as legacy: id {@code key.mclib.dashboard},
 * category {@code key.mclib.category}, default top-row 0
 * ({@code Keyboard.KEY_0} → {@code GLFW_KEY_0}); press opens
 * {@code GuiDashboard.get()}, Ctrl+press jumps straight to the config panel.
 * (Legacy Forge {@code KeyInputEvent} only fired with no GUI open —
 * Fabric's {@code KeyBinding.wasPressed()} has the same gating.)</p>
 *
 * <p>GUI-scale override (legacy {@code onGuiOpen}): opening any
 * {@code GuiBase} saves the current gui scale and forces
 * {@code McLib.userIntefaceScale} (legacy field-name typo kept; on-disk id
 * {@code user_interface_scale}) when &gt; 0; the first non-{@code GuiBase}
 * screen (including {@code null}) restores it. {@code -1} is the
 * "not overridden" sentinel, so a GuiBase→GuiBase chain keeps the saved
 * scale without re-saving. Hooked via a {@code MinecraftClient.setScreen}
 * HEAD mixin — the exact legacy trigger point (Forge {@code GuiOpenEvent}),
 * including the session-static clearing when a non-{@code GuiBase} screen
 * opens while {@code mc.world == null} (reaching a menu after leaving the
 * world) — NOT a world-unload event per se (parity note).
 * Legacy cleared {@code ValueRL.picker}; its port lives on
 * {@code ConfigGuiProviders.picker} (see that class).</p>
 */
public class KeyboardHandler
{
    public KeyBinding dashboard;

    private int lastGuiScale = -1;

    public void register()
    {
        if (this.dashboard != null)
        {
            return;
        }

        this.dashboard = this.registerBinding(new KeyBinding(
            "key.mclib.dashboard",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.mclib.category"));

        ClientTickEvents.END_CLIENT_TICK.register(this::clientTick);
    }

    /**
     * Seam over {@code KeyBindingHelper} (its impl dereferences the live
     * {@code MinecraftClient} — overridable for headless tests).
     */
    protected KeyBinding registerBinding(KeyBinding binding)
    {
        return KeyBindingHelper.registerKeyBinding(binding);
    }

    /**
     * Legacy {@code onKeyboardInput(InputEvent.KeyInputEvent)}.
     */
    private void clientTick(MinecraftClient client)
    {
        while (this.dashboard.wasPressed())
        {
            GuiDashboard dashboard = GuiDashboard.get();

            client.setScreen(dashboard);

            if (Screen.hasControlDown())
            {
                dashboard.panels.setPanel(dashboard.config);
            }
        }
    }

    /**
     * Legacy {@code onGuiOpen(GuiOpenEvent)} — invoked from the
     * {@code MinecraftClient.setScreen} HEAD mixin with the screen about to
     * open ({@code null} = closing back to the game/title flow).
     */
    public void onScreenChange(Screen screen)
    {
        if (screen instanceof GuiBase)
        {
            if (this.lastGuiScale == -1)
            {
                this.lastGuiScale = this.getGuiScale();

                int scale = McLib.userIntefaceScale.get();

                if (scale > 0)
                {
                    this.setGuiScale(scale);
                }
            }
        }
        else
        {
            if (this.lastGuiScale != -1)
            {
                this.setGuiScale(this.lastGuiScale);
                this.lastGuiScale = -1;
            }

            if (this.isWorldNull())
            {
                GuiDashboard.dashboard = null;
                ConfigGuiProviders.picker = null;
                GuiInventoryElement.container = null;

                McLib.proxy.configs.resetServerValues();
                RemoveDashboardPanels.post(new RemoveDashboardPanels());
            }
        }
    }

    /**
     * The saved scale ({@code -1} = "not overridden"), exposed for the
     * state-machine tests.
     */
    public int getSavedGuiScale()
    {
        return this.lastGuiScale;
    }

    /* Seams over the vanilla gui-scale option (legacy int field
     * gameSettings.guiScale → 1.20.4 SimpleOption<Integer> +
     * onResolutionChanged); overridable + headless-safe for unit tests */

    protected boolean isWorldNull()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null || mc.world == null;
    }

    protected int getGuiScale()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.options == null)
        {
            return 0;
        }

        return mc.options.getGuiScale().getValue();
    }

    protected void setGuiScale(int scale)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.options == null)
        {
            return;
        }

        mc.options.getGuiScale().setValue(scale);
        mc.onResolutionChanged();
    }
}
