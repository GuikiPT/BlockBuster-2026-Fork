package mchorse.mclib.client.gui.utils.resizers;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.utils.Area;

/**
 * Port of McLib 2.4.3's {@code BaseResizer} (roadmap P30).
 */
public abstract class BaseResizer implements IResizer, IParentResizer
{
    @Override
    public void preApply(Area area)
    {}

    @Override
    public void apply(Area area)
    {}

    @Override
    public void apply(Area area, IResizer resizer, ChildResizer child)
    {}

    @Override
    public void postApply(Area area)
    {}

    @Override
    public void add(GuiElement parent, GuiElement child)
    {}

    @Override
    public void remove(GuiElement parent, GuiElement child)
    {}
}
