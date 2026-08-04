package mchorse.blockbuster.network.common.scene;

import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Pause/resume a scene — <b>dual purpose</b>: if the sender is currently
 * recording it cancels the recording instead (roadmap P131). Bare base. 1:1
 * wire port of 1.12.2 {@code PacketScenePause.java}.
 */
public class PacketScenePause extends PacketScene
{
    public PacketScenePause()
    {}

    public PacketScenePause(SceneLocation location)
    {
        super(location);
    }
}
