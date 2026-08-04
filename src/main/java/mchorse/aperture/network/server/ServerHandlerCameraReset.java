package mchorse.aperture.network.server;

import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import mchorse.aperture.network.common.PacketCameraReset;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Reset the player's camera capability — profile {@code ""}, timestamp
 * {@code -1} (P182).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerCameraReset.java</p>
 */
public class ServerHandlerCameraReset extends ServerMessageHandler<PacketCameraReset>
{
    @Override
    public void run(ServerPlayerEntity player, PacketCameraReset message)
    {
        ICamera camera = Camera.get(player);

        camera.setCurrentProfile("");
        camera.setCurrentProfileTimestamp(-1);
    }
}
