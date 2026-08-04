package mchorse.aperture.network.server;

import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import mchorse.aperture.network.Dispatcher;
import mchorse.aperture.network.common.PacketRenameCameraProfile;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Rename a server-stored profile (P182). OP-gated; echoes the packet back to
 * the requesting player on success.
 *
 * <p><b>Legacy quirk preserved:</b> the capability's {@code currentProfile} is
 * set to the <b>old</b> name ({@code message.from}) after a rename, not the new
 * one — a verified 1.12.2 bug, kept for byte-parity.</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerRenameCameraProfile.java</p>
 */
public class ServerHandlerRenameCameraProfile extends ServerMessageHandler<PacketRenameCameraProfile>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRenameCameraProfile message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (CameraUtils.renameProfile(message.from, message.to))
        {
            ICamera cap = Camera.get(player);

            cap.setCurrentProfile(message.from);
            cap.setCurrentProfileTimestamp(System.currentTimeMillis());

            Dispatcher.sendTo(message, player);
        }
    }
}
