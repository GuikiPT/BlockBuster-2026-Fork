package mchorse.aperture.network.server;

import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.network.Dispatcher;
import mchorse.aperture.network.common.PacketCameraProfileList;
import mchorse.aperture.network.common.PacketRequestCameraProfiles;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Reply to a client's profile-list request with the server-stored names
 * (P182). OP-gated.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerRequestCameraProfiles.java</p>
 */
public class ServerHandlerRequestCameraProfiles extends ServerMessageHandler<PacketRequestCameraProfiles>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestCameraProfiles message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Dispatcher.sendTo(new PacketCameraProfileList(CameraAPI.getServerProfiles()), player);
    }
}
