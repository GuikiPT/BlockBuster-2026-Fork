package mchorse.mclib.client.gui.framework.elements.modals;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiPromptModal} (roadmap P36). Auto-focuses
 * its text field; refuses to submit an empty string.
 */
public class GuiPromptModal extends GuiModal
{
    public Consumer<String> callback;

    public GuiTextElement text;
    public GuiButtonElement confirm;
    public GuiButtonElement cancel;

    public GuiPromptModal(MinecraftClient mc, IKey label, Consumer<String> callback)
    {
        super(mc, label);

        this.callback = callback;
        this.text = new GuiTextElement(mc, (Consumer<String>) null);
        this.text.flex().relative(this).set(10, 0, 0, 20).y(1, -55).w(1, -20);

        if (GuiBase.getCurrent() != null)
        {
            GuiBase.getCurrent().focus(this.text);
        }

        this.confirm = new GuiButtonElement(mc, IKey.lang("mclib.gui.ok"), (b) -> this.send());
        this.cancel = new GuiButtonElement(mc, IKey.lang("mclib.gui.cancel"), (b) -> this.removeFromParent());

        this.bar.add(this.confirm, this.cancel);
        this.add(this.text);
    }

    public GuiPromptModal filename()
    {
        this.text.filename();

        return this;
    }

    public GuiPromptModal setValue(String value)
    {
        this.text.setText(value);

        return this;
    }

    public void send()
    {
        String text = this.text.field.getText();

        if (!text.isEmpty())
        {
            this.removeFromParent();

            if (this.callback != null)
            {
                this.callback.accept(text);
            }
        }
    }

    @Override
    public boolean keyTyped(GuiContext context)
    {
        if (super.keyTyped(context))
        {
            return true;
        }

        if (context.keyCode == LegacyKeyCodes.KEY_RETURN)
        {
            this.confirm.clickItself(context);

            return true;
        }
        else if (context.keyCode == LegacyKeyCodes.KEY_ESCAPE)
        {
            this.cancel.clickItself(context);

            return true;
        }

        return false;
    }
}
