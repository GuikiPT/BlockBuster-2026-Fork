package mchorse.blockbuster.aperture.network.common;

import mchorse.blockbuster.network.common.scene.PacketScene;
import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * C→S request for the synced scene's length and audio shift (roadmap P185.1).
 *
 * <p>Sent when the camera editor opens on an attached scene; the server replies
 * with {@link PacketSceneLength}, which restores the editor's scrub bounds and
 * the director options' audio-shift trackpad. Carries nothing beyond the
 * {@link PacketScene} location payload.</p>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/network/common/PacketRequestLength.java</p>
 */
public class PacketRequestLength extends PacketScene
{
    public PacketRequestLength()
    {}

    public PacketRequestLength(SceneLocation location)
    {
        super(location);
    }
}
