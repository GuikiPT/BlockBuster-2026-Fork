package mchorse.blockbuster.aperture.network.server;

import mchorse.blockbuster.aperture.network.common.PacketRequestLength;
import mchorse.blockbuster.aperture.network.common.PacketSceneLength;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server receiver for {@link PacketRequestLength} (roadmap P185.1). 1:1 port of
 * 1.12.2 {@code aperture/network/server/ServerHandlerRequestLength.java}.
 *
 * <p>Silent no-op when the scene cannot be resolved — legacy sent no reply and
 * the camera editor simply keeps its current scrub bounds.</p>
 */
public class ServerHandlerRequestLength extends ServerMessageHandler<PacketRequestLength>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestLength message)
    {
        Scene scene = message.get(player.getWorld());

        if (scene != null)
        {
            Dispatcher.sendTo(new PacketSceneLength(scene.getMaxLength(), scene.getAudioShift()), player);
        }
    }
}
