package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.recording.PacketUnloadRecordings;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketUnloadRecordings} (roadmap P116) — clears the
 * entire client record cache (world change / unload).
 */
public class ClientHandlerUnloadRecordings extends ClientMessageHandler<PacketUnloadRecordings>
{
    @Override
    public void run(ClientPlayerEntity player, PacketUnloadRecordings message)
    {
        ClientProxy.manager.records.clear();
    }
}
