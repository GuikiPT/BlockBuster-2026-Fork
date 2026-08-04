package mchorse.blockbuster.network.common.scene;

import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Toggle scene playback (roadmap P131). Bare base; the server toggles the
 * named scene's playback in the sender's world. 1:1 wire port of 1.12.2
 * {@code PacketScenePlayback.java}.
 */
public class PacketScenePlayback extends PacketScene
{
    public PacketScenePlayback()
    {}

    public PacketScenePlayback(SceneLocation location)
    {
        super(location);
    }
}
