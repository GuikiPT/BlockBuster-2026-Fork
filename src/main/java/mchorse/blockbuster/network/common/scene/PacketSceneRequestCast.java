package mchorse.blockbuster.network.common.scene;

import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Force-request the cast of a scene by the director (roadmap P131). Bare base:
 * the server reloads the scene from disk (bypassing the cache) and replies with
 * a {@link PacketSceneCast}. 1:1 wire port of 1.12.2
 * {@code PacketSceneRequestCast.java}.
 */
public class PacketSceneRequestCast extends PacketScene
{
    public PacketSceneRequestCast()
    {}

    public PacketSceneRequestCast(SceneLocation location)
    {
        super(location);
    }
}
