package mchorse.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.network.common.PacketAperture;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Server-mode handshake receiver (P182): flips {@code ClientProxy.server} so
 * the profile manager proxies profiles to the Aperture server.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerAperture.java</p>
 */
public class ClientHandlerAperture extends ClientMessageHandler<PacketAperture>
{
    @Override
    public void run(ClientPlayerEntity player, PacketAperture message)
    {
        ClientProxy.server = true;
    }
}
