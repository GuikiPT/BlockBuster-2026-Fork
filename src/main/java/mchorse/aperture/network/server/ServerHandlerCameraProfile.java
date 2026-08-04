package mchorse.aperture.network.server;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.CameraUtils;
import mchorse.aperture.capabilities.camera.Camera;
import mchorse.aperture.capabilities.camera.ICamera;
import mchorse.aperture.network.common.PacketCameraProfile;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import mchorse.mclib.utils.Patterns;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Save a camera profile sent by a client (P182). OP-gated; the filename is
 * validated against McLib {@code Patterns.FILENAME} before touching disk. On
 * success the player's capability records the profile name + fresh timestamp.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/server/ServerHandlerCameraProfile.java</p>
 */
public class ServerHandlerCameraProfile extends ServerMessageHandler<PacketCameraProfile>
{
    @Override
    public void run(ServerPlayerEntity player, PacketCameraProfile message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (!Patterns.FILENAME.matcher(message.filename).matches())
        {
            Aperture.l10n.error(player, "profile.wrong_filename", message.filename);

            return;
        }

        if (CameraUtils.saveCameraProfile(message.filename, CameraUtils.toJSON(message.profile), player))
        {
            ICamera cap = Camera.get(player);

            cap.setCurrentProfile(message.filename);
            cap.setCurrentProfileTimestamp(System.currentTimeMillis());

            Aperture.l10n.success(player, "profile.save", message.filename);
        }
    }
}
