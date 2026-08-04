package mchorse.aperture.network.server;

import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.network.common.PacketLoadCameraProfile;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Load a server-stored profile for the requesting client (P182). OP-gated;
 * delegates to the staleness-aware {@code CameraUtils.sendProfileToPlayer}
 * (play = false).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerLoadCameraProfile.java</p>
 */
public class ServerHandlerLoadCameraProfile extends ServerMessageHandler<PacketLoadCameraProfile>
{
    @Override
    public void run(ServerPlayerEntity player, PacketLoadCameraProfile message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        CameraUtils.sendProfileToPlayer(message.filename, player, false, message.force);
    }
}
