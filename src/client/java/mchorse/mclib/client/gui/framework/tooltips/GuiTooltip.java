package mchorse.mclib.client.gui.framework.tooltips;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.utils.Area;

/**
 * Port of McLib 2.4.3's {@code GuiTooltip} (roadmap P29; tooltip styles land
 * in P38). Holds the element pending a tooltip this frame plus its area in
 * global (screen) coordinates.
 */
public class GuiTooltip
{
    public GuiElement element;
    public Area area = new Area();

    public void set(GuiContext context, GuiElement element)
    {
        this.element = element;

        if (element != null)
        {
            this.area.copy(element.area);
            this.area.x = context.globalX(this.area.x);
            this.area.y = context.globalY(this.area.y);
        }
    }

    public void draw(ITooltip tooltip, GuiContext context)
    {
        if (this.element == null || tooltip == null)
        {
            return;
        }

        tooltip.drawTooltip(context);
    }

    public void drawTooltip(GuiContext context)
    {
        if (this.element != null)
        {
            this.element.drawTooltip(context, this.area);
        }
    }
}
