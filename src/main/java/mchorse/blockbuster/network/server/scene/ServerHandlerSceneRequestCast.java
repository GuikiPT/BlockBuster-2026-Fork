package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.blockbuster.network.common.scene.PacketSceneRequestCast;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketSceneRequestCast} (roadmap P131). OP-gated +
 * non-empty. <b>Force-loads</b> the scene from disk via
 * {@code CommonProxy.scenes.load(...)} (bypassing the live cache) and replies
 * with a default-open {@link PacketSceneCast}. 1:1 behavior port of 1.12.2
 * {@code ServerHandlerSceneRequestCast.java}.
 */
public class ServerHandlerSceneRequestCast extends ServerMessageHandler<PacketSceneRequestCast>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSceneRequestCast message)
    {
        if (!OpHelper.isPlayerOp(player) || message.location.isEmpty())
        {
            return;
        }

        try
        {
            Scene scene = CommonProxy.scenes.load(message.location.getFilename());

            Dispatcher.sendTo(new PacketSceneCast(new SceneLocation(scene)), player);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
