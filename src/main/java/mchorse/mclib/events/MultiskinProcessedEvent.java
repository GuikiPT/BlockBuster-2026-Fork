package mchorse.mclib.events;

import mchorse.mclib.utils.resources.MultiResourceLocation;

import java.awt.image.BufferedImage;

/**
 * Fired after a {@link MultiResourceLocation} finishes CPU-side compositing,
 * before the composited image is uploaded to the GPU (roadmap P91) — lets mods
 * post-process the combined texture. Legacy was a Forge {@code @SideOnly(CLIENT)}
 * event on {@code McLib.EVENT_BUS}; the port dispatches it through
 * {@link McLibEvents#MULTISKIN_PROCESSED} (still fired on the client render
 * thread, see {@code TextureProcessor.postProcess}).
 *
 * <p>The canonical listener (wired in the Blockbuster client) is
 * {@code ModelExtrudedLayer.forceReload(location, image)} (S6/P79), which
 * regenerates extruded 3D skin layers whenever a multiskin re-composites.</p>
 */
public class MultiskinProcessedEvent
{
    public final MultiResourceLocation location;
    public final BufferedImage image;

    public MultiskinProcessedEvent(MultiResourceLocation location, BufferedImage image)
    {
        this.location = location;
        this.image = image;
    }
}
