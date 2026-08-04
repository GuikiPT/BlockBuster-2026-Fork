package mchorse.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.network.common.PacketRenameCameraProfile;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Apply a server-confirmed rename to the editor's profile list (P182).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerRenameCameraProfile.java</p>
 */
public class ClientHandlerRenameCameraProfile extends ClientMessageHandler<PacketRenameCameraProfile>
{
    @Override
    public void run(ClientPlayerEntity player, PacketRenameCameraProfile message)
    {
        ClientProxy.getCameraEditor().profiles.rename(new ServerDestination(message.from), message.to);
    }
}
