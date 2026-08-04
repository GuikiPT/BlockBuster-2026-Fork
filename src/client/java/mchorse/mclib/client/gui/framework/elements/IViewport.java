package mchorse.mclib.client.gui.framework.elements;

import mchorse.mclib.client.gui.framework.elements.utils.IViewportStack;

/**
 * Port of McLib 2.4.3's {@code IViewport} (roadmap P29).
 */
public interface IViewport
{
    public void apply(IViewportStack stack);

    public void unapply(IViewportStack stack);
}
