package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.network.common.recording.PacketRequestFrames;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Serves an actor's requested frames back to the client (roadmap P115) via
 * {@code RecordUtils.sendRequestedRecord}.
 */
public class ServerHandlerRequestFrames extends ServerMessageHandler<PacketRequestFrames>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestFrames message)
    {
        RecordUtils.sendRequestedRecord(message.id, message.filename, player);
    }
}
