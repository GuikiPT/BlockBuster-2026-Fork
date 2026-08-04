package mchorse.aperture.network.server;

import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.network.Dispatcher;
import mchorse.aperture.network.common.PacketRemoveCameraProfile;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Delete a server-stored profile (P182). OP-gated; echoes the packet back to
 * the requesting player <b>only</b> on a successful delete (the client then
 * drops the entry and re-selects).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerRemoveCameraProfile.java</p>
 */
public class ServerHandlerRemoveCameraProfile extends ServerMessageHandler<PacketRemoveCameraProfile>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRemoveCameraProfile message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (CameraUtils.removeProfile(message.profile))
        {
            Dispatcher.sendTo(message, player);
        }
    }
}
