package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.network.common.recording.PacketRequestRecording;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketRequestRecording} (roadmap P116) — the server
 * half of the record-request RPC. Replies with a {@code PacketFramesLoad}
 * carrying the same callback id (the send lives in {@link RecordUtils#sendRecordTo},
 * completed in P115).
 *
 * <p>Legacy kept the client-side {@code requestRecording(filename,
 * Consumer<Record>)} helper on this same class behind {@code @SideOnly(CLIENT)}.
 * Split source sets forbid that — it registers its consumer through
 * {@code ClientHandlerFramesLoad}, which is client-only — so on Fabric the
 * helper lives on {@code ClientHandlerFramesLoad} itself (same move the
 * overwrite flow made with {@code ClientHandlerFramesOverwrite}).</p>
 */
public class ServerHandlerRequestRecording extends ServerMessageHandler<PacketRequestRecording>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestRecording message)
    {
        if (message.getCallbackID().isPresent())
        {
            this.sendRecord(message.getFilename(), player, message.getCallbackID().get());
        }
        else
        {
            this.sendRecord(message.getFilename(), player, -1);
        }
    }

    /**
     * The reply send, isolated so the callback-id forwarding is headless-testable
     * without a live player connection (same seam convention as
     * {@code ServerHandlerFramesOverwrite.process}). {@code -1} is
     * {@link RecordUtils#sendRecordTo(String, ServerPlayerEntity)}'s own
     * no-callback sentinel, so both branches land on one call.
     */
    protected void sendRecord(String filename, ServerPlayerEntity player, int callbackID)
    {
        RecordUtils.sendRecordTo(filename, player, callbackID);
    }
}
