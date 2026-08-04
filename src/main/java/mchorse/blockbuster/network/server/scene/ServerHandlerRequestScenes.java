package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketRequestScenes;
import mchorse.blockbuster.network.common.scene.PacketScenes;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketRequestScenes} (roadmap P131). OP-gated;
 * replies with the on-disk scene file list. 1:1 behavior port of 1.12.2
 * {@code ServerHandlerRequestScenes.java}.
 */
public class ServerHandlerRequestScenes extends ServerMessageHandler<PacketRequestScenes>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestScenes message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Dispatcher.sendTo(new PacketScenes(CommonProxy.scenes.sceneFiles()), player);
    }
}
