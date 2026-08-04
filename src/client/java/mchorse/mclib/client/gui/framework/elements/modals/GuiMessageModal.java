package mchorse.mclib.client.gui.framework.elements.modals;

import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiMessageModal} (roadmap P36) — a single
 * button; either Enter or ESC dismisses.
 */
public class GuiMessageModal extends GuiModal
{
    public GuiButtonElement button;

    public GuiMessageModal(MinecraftClient mc, IKey label)
    {
        super(mc, label);

        this.button = new GuiButtonElement(mc, IKey.lang("mclib.gui.ok"), (b) -> this.removeFromParent());

        this.bar.add(this.button);
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (super.keyTyped(context))
        {
            return true;
        }

        if (context.keyCode == LegacyKeyCodes.KEY_RETURN || context.keyCode == LegacyKeyCodes.KEY_ESCAPE)
        {
            this.button.clickItself(context);

            return true;
        }

        return false;
    }
}
