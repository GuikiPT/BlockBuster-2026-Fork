package mchorse.mclib.client.gui.framework.elements;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;

/**
 * Port of McLib 2.4.3's {@code IFocusedGuiElement} (roadmap P29).
 */
public interface IFocusedGuiElement
{
    public boolean isFocused();

    public void focus(GuiContext context);

    public void unfocus(GuiContext context);

    public void selectAll(GuiContext context);

    public void unselect(GuiContext context);
}
