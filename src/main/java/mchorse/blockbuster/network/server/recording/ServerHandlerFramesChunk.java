package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.network.common.recording.PacketFramesChunk;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.FrameChunk;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.List;

/**
 * Server-side reassembly of a chunked frame upload (roadmap P115).
 *
 * <p>Chunks accumulate in {@code CommonProxy.manager.chunks} keyed by filename.
 * <b>Silently dropped when no server {@code Record} exists</b> for the filename
 * (the record is created by the recording flow, P109, before the client
 * uploads). There is <b>no permission check</b> on upload — legacy behavior
 * (audit note). When the chunk store {@code isFilled()}: registers the record
 * in the player's {@code IRecording}, compiles the frames onto the server
 * record, saves, then {@code fillMissingActions()} and removes the buffer.</p>
 *
 * <p><b>P279 deviation from 1.12.2:</b> an upload that compiles to an empty
 * frame list no longer replaces a record that has frames. See the inline
 * comment — this assignment is how the pre-P244 builds (client frame capture
 * dark ⇒ every upload empty) silently emptied existing {@code .dat} files, and
 * a zero-frame record then crashed the client on playback.</p>
 */
public class ServerHandlerFramesChunk extends ServerMessageHandler<PacketFramesChunk>
{
    @Override
    public void run(ServerPlayerEntity player, PacketFramesChunk message)
    {
        Record serverRecord = CommonProxy.manager.records.get(message.filename);
        FrameChunk chunk = CommonProxy.manager.chunks.get(message.filename);

        if (serverRecord == null)
        {
            return;
        }

        /* P279: the buffer is keyed by filename only and legacy never cleaned it
         * up on failure, so a half-delivered upload left a FrameChunk that could
         * never fill — and it then swallowed *every* later upload for that
         * filename, silently. A chunk whose shape disagrees with the incoming
         * message can only be that stale buffer (one take uploads one shape), so
         * it is replaced rather than added to. */
        if (chunk != null && (chunk.count != message.count || chunk.offset != message.offset))
        {
            Blockbuster.LOGGER.warn(
                "Dropping a stale frame-upload buffer for record '{}' ({} chunks @ offset {}) — "
                + "a new upload arrived with {} chunks @ offset {}.",
                message.filename, chunk.count, chunk.offset, message.count, message.offset);

            chunk = null;
        }

        if (chunk == null)
        {
            chunk = new FrameChunk(message.count, message.offset);

            CommonProxy.manager.chunks.put(message.filename, chunk);
        }

        chunk.add(message.index, message.frames);

        if (chunk.isFilled())
        {
            try
            {
                Recording.get(player).addRecording(message.filename, System.currentTimeMillis());

                List<Frame> compiled = chunk.compile(serverRecord.frames);

                if (!acceptUpload(compiled, serverRecord.frames, message.filename))
                {
                    CommonProxy.manager.chunks.remove(message.filename);

                    return;
                }

                serverRecord.frames = compiled;
                serverRecord.save(RecordUtils.replayFile(message.filename));
                serverRecord.fillMissingActions();

                CommonProxy.manager.chunks.remove(message.filename);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * P279 upload policy — whether a reassembled frame upload may be written
     * onto the server record.
     *
     * <p>1.12.2 assigned {@code record.frames = chunk.compile(...)}
     * unconditionally. That is exactly how the pre-P244 builds destroyed
     * existing recordings: client frame capture was dark, so every
     * {@code PacketFramesChunk} upload compiled to an empty list, and this
     * assignment replaced a good take with zero frames and saved it. Three of
     * the reporter's {@code .dat} files were left permanently empty that way,
     * and playing one of them crashed the client outright
     * ({@code RecordMorph.previewActor}'s {@code % record.getLength()}).</p>
     *
     * <p>Refusing the empty overwrite is strictly protective — it cannot fire on
     * a healthy take, and the record on disk stays playable. An empty upload
     * onto an already-empty record is still accepted (nothing to lose, and it is
     * the parity path), but it is logged: a take that captured no frames at all
     * is always a defect worth seeing in the log.</p>
     *
     * <p>Static and package-visible so the policy is headless-testable — the
     * enclosing {@link #run} needs a live {@link ServerPlayerEntity}.</p>
     *
     * @param compiled the reassembled upload.
     * @param existing the frames the server record currently holds.
     * @return whether {@code compiled} may replace {@code existing}.
     */
    static boolean acceptUpload(List<Frame> compiled, List<Frame> existing, String filename)
    {
        if (!compiled.isEmpty())
        {
            return true;
        }

        if (!existing.isEmpty())
        {
            Blockbuster.LOGGER.warn(
                "Refusing to overwrite record '{}' ({} frames) with an empty frame upload — "
                + "the client captured no frames for this take.",
                filename, existing.size());

            return false;
        }

        Blockbuster.LOGGER.warn(
            "Record '{}' was saved with an empty frame list — the client captured no frames for this take.",
            filename);

        return true;
    }
}
