package mchorse.mclib.config.values;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.config.gui.GuiConfigPanel;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Port of McLib 2.4.3's {@code IConfigGuiProvider} (roadmap P44).
 *
 * <p>Legacy {@code Value*} classes implemented this directly (with
 * {@code @SideOnly} on the method). The port's value classes live in the
 * main source set and cannot reference GUI classes, so the per-type widget
 * bodies moved to the client-side factory registry
 * ({@code mchorse.mclib.config.gui.ConfigGuiProviders}); this interface
 * remains (client source set, legacy package kept) for <b>client-side</b>
 * values that can still self-provide — e.g.
 * {@code ModKeybinds.KeybindCategory} (P39) and legacy
 * {@code ValueGUI}-style values.</p>
 */
public interface IConfigGuiProvider
{
    public List<GuiElement> getFields(MinecraftClient mc, GuiConfigPanel gui);
}
