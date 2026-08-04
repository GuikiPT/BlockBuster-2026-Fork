package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.FramesUpload;
import mchorse.blockbuster.network.common.recording.PacketFramesChunk;
import mchorse.blockbuster.network.common.recording.PacketPlayerRecording;
import mchorse.blockbuster.recording.RecordRecorder;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketPlayerRecording} (roadmap P116/P115).
 *
 * <p>Updates the recording overlay and starts/stops the local player's frame
 * capture on the client record-manager mirror. On stop (unless canceled) the
 * captured frames are uploaded chunked to the server through
 * {@link #sendFrames(RecordRecorder)} — the {@code cap = 400} split lives in
 * {@link FramesUpload}, each chunk goes out as its own
 * {@link PacketFramesChunk} and the dispatcher re-frames anything over the
 * serverbound payload cap onto the P25 chunked transport.</p>
 */
public class ClientHandlerPlayerRecording extends ClientMessageHandler<PacketPlayerRecording>
{
    @Override
    public void run(ClientPlayerEntity player, PacketPlayerRecording message)
    {
        ClientProxy.recordingOverlay.setVisible(message.recording);
        ClientProxy.recordingOverlay.setCaption(message.filename, true);

        if (message.recording)
        {
            ClientProxy.manager.record(message.filename, player, Mode.FRAMES, false, false, message.offset, null);
        }
        else
        {
            if (!message.canceled)
            {
                this.sendFrames(ClientProxy.manager.recorders.get(player));
            }

            ClientProxy.manager.halt(player, false, false, message.canceled);
        }
    }

    /**
     * Send chunked frames to the server (P115: {@code PacketFramesChunk} upload,
     * {@code cap = 400}).
     *
     * <p>Legacy body verbatim, with the chunk math hoisted into
     * {@link FramesUpload#split(String, java.util.List, int)} (which pins the
     * "below 500"/cap-400 and empty-trailing-chunk quirks). Two total-reader
     * deviations from 1.12.2, neither observable on a healthy path:</p>
     *
     * <ul>
     *   <li>{@code recorder}/{@code recorder.record} null → no-op. 1.12
     *   dereferenced both unconditionally; on the port the recorder map lookup
     *   can miss when a stop packet arrives for a player who never started.</li>
     *   <li>{@code FramesUpload} lengths the split on {@code frames.size()}
     *   where 1.12 used {@code Record.getLength()}
     *   ({@code max(actions, frames)}) — identical for a FRAMES-mode recorder
     *   upload (no actions are captured), and it removes the index overrun
     *   1.12 would hit if actions ever outnumbered frames.</li>
     * </ul>
     *
     * <p>Protected rather than private as the headless test seam — the
     * enclosing {@link #run} needs a live {@code ClientPlayerEntity}.</p>
     */
    protected void sendFrames(RecordRecorder recorder)
    {
        if (recorder == null || recorder.record == null)
        {
            return;
        }

        Record record = recorder.record;

        for (PacketFramesChunk chunk : FramesUpload.split(record.filename, record.frames, recorder.offset))
        {
            Dispatcher.sendToServer(chunk);
        }
    }
}
