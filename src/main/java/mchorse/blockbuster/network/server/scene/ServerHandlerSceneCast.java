package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketSceneCast} (roadmap P131). OP-gated; bails on
 * an empty location. Saves the embedded scene to disk and records it as the
 * sender's last-edited scene (which drives the login-sync cast, P131.1). 1:1
 * behavior port of 1.12.2 {@code ServerHandlerSceneCast.java} — the save
 * exception is swallowed with a stack trace, exactly like legacy.
 */
public class ServerHandlerSceneCast extends ServerMessageHandler<PacketSceneCast>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSceneCast message)
    {
        if (!OpHelper.isPlayerOp(player) || message.location.isEmpty())
        {
            return;
        }

        try
        {
            CommonProxy.scenes.save(message.location.getFilename(), message.location.getScene());
            Recording.get(player).setLastScene(message.location.getFilename());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
