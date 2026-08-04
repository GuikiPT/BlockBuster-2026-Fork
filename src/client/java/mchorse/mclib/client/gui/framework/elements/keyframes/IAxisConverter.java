package mchorse.mclib.client.gui.framework.elements.keyframes;

import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.utils.keyframes.Keyframe;

/**
 * Port of McLib 2.4.3's {@code IAxisConverter} (roadmap P43) — lets a
 * consumer (e.g. Aperture's camera editor, S15) remap the horizontal tick
 * axis to another unit (seconds, frames at another rate, ...). The legacy
 * {@code @SideOnly} markers are dropped — this whole package lives in the
 * client source set.
 */
public interface IAxisConverter
{
    public String format(double value);

    public double from(double x);

    public double to(double x);

    public void updateField(GuiTrackpadElement element);

    public boolean forceInteger(Keyframe keyframe, Selection selection, boolean forceInteger);
}
