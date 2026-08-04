package mchorse.mclib.client.gui.utils.resizers;

import mchorse.mclib.client.gui.utils.Area;

/**
 * Port of McLib 2.4.3's {@code IParentResizer} (roadmap P30).
 */
public interface IParentResizer
{
    public void apply(Area area, IResizer resizer, ChildResizer child);
}
