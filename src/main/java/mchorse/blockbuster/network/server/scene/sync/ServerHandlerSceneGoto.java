package mchorse.blockbuster.network.server.scene.sync;

import mchorse.blockbuster.network.common.scene.sync.PacketSceneGoto;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketSceneGoto} (roadmap P131). OP-gated;
 * null-guarded {@code scene.goTo(tick, actions)}. 1:1 behavior port of 1.12.2
 * {@code sync/ServerHandlerSceneGoto.java} (already null-safe in legacy).
 */
public class ServerHandlerSceneGoto extends ServerMessageHandler<PacketSceneGoto>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSceneGoto message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Scene scene = message.get(player.getWorld());

        if (scene != null)
        {
            scene.goTo(message.tick, message.actions);
        }
    }
}
