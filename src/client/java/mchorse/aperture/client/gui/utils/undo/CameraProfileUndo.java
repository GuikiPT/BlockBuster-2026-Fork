package mchorse.aperture.client.gui.utils.undo;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.client.gui.GuiPlaybackScrub;
import mchorse.mclib.utils.undo.IUndo;

/**
 * Base camera profile undo (P183) — snapshots the timeline cursor and view
 * range so undo/redo restores what the user was looking at.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/utils/undo/CameraProfileUndo.java
 */
public abstract class CameraProfileUndo implements IUndo<CameraProfile>
{
    public int cursor;
    public double viewMin;
    public double viewMax;

    public IUndo<CameraProfile> view(GuiPlaybackScrub scrub)
    {
        this.cursor = scrub.value;
        this.viewMin = scrub.scale.getMinValue();
        this.viewMax = scrub.scale.getMaxValue();

        return this;
    }
}
