package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.recording.PacketUnloadFrames;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Evicts one record from the client record cache (roadmap P115).
 */
public class ClientHandlerUnloadFrames extends ClientMessageHandler<PacketUnloadFrames>
{
    @Override
    public void run(ClientPlayerEntity player, PacketUnloadFrames message)
    {
        ClientProxy.manager.records.remove(message.filename);
    }
}
