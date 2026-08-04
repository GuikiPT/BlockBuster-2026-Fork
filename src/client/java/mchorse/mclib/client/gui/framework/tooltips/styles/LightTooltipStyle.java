package mchorse.mclib.client.gui.framework.tooltips.styles;

import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.utils.ColorUtils;

/**
 * Port of McLib 2.4.3's {@code LightTooltipStyle} (roadmap P38) — white
 * background, black text.
 */
public class LightTooltipStyle extends TooltipStyle
{
    @Override
    public void drawBackground(Area area)
    {
        GuiDraw.drawDropShadow(area.x, area.y, area.ex(), area.ey(), 4, ColorUtils.HALF_BLACK, 0);
        area.draw(0xffffffff);
    }

    @Override
    public int getTextColor()
    {
        return 0;
    }

    @Override
    public int getForegroundColor()
    {
        return 0;
    }
}
