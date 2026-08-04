package mchorse.mclib.client.gui.framework.elements.input;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.IFocusedGuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.GuiTextField;
import mchorse.mclib.utils.Color;
import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiBaseTextElement} (roadmap P33), wrapping the
 * bundled {@link GuiTextField}. Legacy toggled LWJGL2's global
 * {@code Keyboard.enableRepeatEvents} on focus/unfocus — the 1.20.4
 * equivalent is the P29 {@code GuiContext.repeatEvents} flag.
 */
public abstract class GuiBaseTextElement extends GuiElement implements IFocusedGuiElement
{
    public GuiTextField field;

    public GuiBaseTextElement(MinecraftClient mc)
    {
        super(mc);

        this.field = new GuiTextField(0, this.font, 0, 0, 0, 0);
    }

    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        this.field.setEnabled(enabled);
    }

    @Override
    public void setVisible(boolean visible)
    {
        super.setVisible(visible);
        this.field.setVisible(visible);
    }

    public void setTextColor(Color color)
    {
        this.field.setTextColor(color.getRGBAColor());
    }

    public void setTextColor(int color)
    {
        this.field.setTextColor(color);
    }

    public void setText(String textIn)
    {
        this.field.setText(textIn);
    }

    @Override
    public boolean isFocused()
    {
        return this.field.isFocused();
    }

    @Override
    public void focus(GuiContext context)
    {
        this.field.setFocused(true);

        /* Legacy Keyboard.enableRepeatEvents(true) */
        if (context != null)
        {
            context.repeatEvents = true;
        }
    }

    @Override
    public void unfocus(GuiContext context)
    {
        this.field.setFocused(false);

        /* Legacy Keyboard.enableRepeatEvents(false) */
        if (context != null)
        {
            context.repeatEvents = false;
        }
    }

    @Override
    public void selectAll(GuiContext context)
    {
        this.field.setCursorPosition(0);
        this.field.setSelectionPos(this.field.getText().length());
    }

    @Override
    public void unselect(GuiContext context)
    {
        this.field.setSelectionPos(this.field.getCursorPosition());
    }
}
