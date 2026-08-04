package mchorse.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.destination.AbstractDestination;
import mchorse.aperture.camera.destination.ClientDestination;
import mchorse.aperture.network.common.PacketCameraState;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Start or stop camera playback (P182). {@code "client:"}-domain names load
 * from disk first; an empty/unknown filename falls back to
 * {@code control.currentProfile}; {@code toPlay=false} stops the runner.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerCameraState.java</p>
 */
public class ClientHandlerCameraState extends ClientMessageHandler<PacketCameraState>
{
    @Override
    public void run(ClientPlayerEntity player, PacketCameraState message)
    {
        if (message.toPlay)
        {
            AbstractDestination destination = AbstractDestination.create(message.filename);

            if (destination instanceof ClientDestination)
            {
                new ClientDestination(message.filename).load();
            }

            if (message.filename.isEmpty())
            {
                ClientProxy.runner.start(ClientProxy.control.currentProfile);
            }
            else
            {
                CameraProfile profile = ClientProxy.control.getProfile(destination);

                if (profile == null)
                {
                    ClientProxy.runner.start(ClientProxy.control.currentProfile);
                }
                else
                {
                    ClientProxy.runner.start(profile);
                }
            }
        }
        else
        {
            ClientProxy.runner.stop();
        }
    }
}
