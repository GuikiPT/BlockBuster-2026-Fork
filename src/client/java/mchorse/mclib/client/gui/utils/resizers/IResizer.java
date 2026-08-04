package mchorse.mclib.client.gui.utils.resizers;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.utils.Area;

/**
 * Port of McLib 2.4.3's {@code IResizer} (roadmap P30). {@code @SideOnly}
 * dropped — client source set.
 */
public interface IResizer
{
    public void preApply(Area area);

    public void apply(Area area);

    public void postApply(Area area);

    public void add(GuiElement parent, GuiElement child);

    public void remove(GuiElement parent, GuiElement child);

    public int getX();

    public int getY();

    public int getW();

    public int getH();
}
