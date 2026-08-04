package mchorse.mclib.client.gui.framework.tooltips;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.tooltips.styles.TooltipStyle;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.MathUtils;

import java.util.List;

/**
 * Port of McLib 2.4.3's {@code LabelTooltip} (roadmap P38).
 *
 * Placement contract (pinned by test): {@code Direction} anchor with a 6px
 * gap, auto-flip to the opposite side when the tooltip rect would overlap
 * the element, then clamp to the screen with a 3px margin — flip first,
 * clamp after. The element's area arrives in global coordinates (captured by
 * {@code GuiTooltip.set} inside the viewport application).
 */
public class LabelTooltip implements ITooltip
{
    public IKey label;
    public int width = 200;
    public Direction direction;

    public LabelTooltip(IKey label, Direction direction)
    {
        this.label = label;
        this.direction = direction;
    }

    public LabelTooltip(IKey label, int width, Direction direction)
    {
        this(label, direction);
        this.width = width;
    }

    @Override
    public void drawTooltip(GuiContext context)
    {
        String label = this.label.get();

        if (label.isEmpty())
        {
            return;
        }

        List<String> strings = GuiDraw.listFormattedStringToWidth(label, this.width, (s) -> GuiDraw.textWidth(context.font, s));

        if (strings.isEmpty())
        {
            return;
        }

        TooltipStyle style = TooltipStyle.get();
        Direction dir = this.direction;
        Area area = context.tooltip.area;

        this.calculate(context, strings, dir, area, Area.SHARED);

        if (Area.SHARED.intersects(area))
        {
            this.calculate(context, strings, dir.opposite(), area, Area.SHARED);
        }

        Area.SHARED.offset(3);
        style.drawBackground(Area.SHARED);
        Area.SHARED.offset(-3);

        for (String line : strings)
        {
            GuiDraw.drawString(context.font, line, Area.SHARED.x, Area.SHARED.y, style.getTextColor());
            Area.SHARED.y += GuiDraw.fontHeight(context.font) + 3;
        }
    }

    /**
     * Legacy anchoring math, extracted verbatim; exposed for headless
     * placement tests (writes into {@code targetArea}).
     */
    public void calculate(GuiContext context, List<String> strings, Direction dir, Area elementArea, Area targetArea)
    {
        int w = strings.size() == 1 ? GuiDraw.textWidth(context.font, strings.get(0)) : this.width;
        int h = (GuiDraw.fontHeight(context.font) + 3) * strings.size() - 3;
        int x = elementArea.x(dir.anchorX) - (int) (w * (1 - dir.anchorX)) + 6 * dir.factorX;
        int y = elementArea.y(dir.anchorY) - (int) (h * (1 - dir.anchorY)) + 6 * dir.factorY;

        x = MathUtils.clamp(x, 3, context.screen.width - w - 3);
        y = MathUtils.clamp(y, 3, context.screen.height - h - 3);

        targetArea.set(x, y, w, h);
    }
}
