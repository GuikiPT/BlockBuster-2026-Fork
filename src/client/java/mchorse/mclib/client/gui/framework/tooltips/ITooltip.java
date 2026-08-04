package mchorse.mclib.client.gui.framework.tooltips;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;

/**
 * Port of McLib 2.4.3's {@code ITooltip} (roadmap P29; the tooltip system
 * itself is P38).
 */
public interface ITooltip
{
    public void drawTooltip(GuiContext context);
}
