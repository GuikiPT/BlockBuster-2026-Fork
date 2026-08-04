package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.scene.PacketScenePlayback;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketScenePlayback} (roadmap P131). OP-gated +
 * non-empty; toggles the named scene's playback in the sender's world. 1:1
 * behavior port of 1.12.2 {@code ServerHandlerScenePlayback.java}.
 */
public class ServerHandlerScenePlayback extends ServerMessageHandler<PacketScenePlayback>
{
    @Override
    public void run(ServerPlayerEntity player, PacketScenePlayback message)
    {
        if (!OpHelper.isPlayerOp(player) || message.location.isEmpty())
        {
            return;
        }

        CommonProxy.scenes.toggle(message.location.getFilename(), player.getWorld());
    }
}
