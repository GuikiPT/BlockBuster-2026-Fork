package mchorse.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.network.common.PacketRemoveCameraProfile;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Drop a server-confirmed removed profile from the editor's list (P182).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerRemoveCameraProfile.java</p>
 */
public class ClientHandlerRemoveCameraProfile extends ClientMessageHandler<PacketRemoveCameraProfile>
{
    @Override
    public void run(ClientPlayerEntity player, PacketRemoveCameraProfile message)
    {
        ClientProxy.getCameraEditor().profiles.remove(new ServerDestination(message.profile));
    }
}
