package mchorse.blockbuster_pack.client;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.client.recording.ClientHandlerFramesLoad;
import mchorse.blockbuster_pack.morphs.RecordMorph;

/**
 * S22 P235 — the client half of {@link RecordMorph}.
 *
 * <p>{@code RecordMorph} lives in the common source set (records and record
 * morphs are saved/loaded server-side), but its ghost actor is client-only: it
 * looks the record up in the <b>client</b> record cache and, when it is not
 * there yet, asks the server to stream it back. P161 shipped both of those as
 * static seams and nothing ever assigned them, so on a real client
 * {@code recordSource} returned {@code null} forever, the "record not available
 * yet" branch was taken on every tick, and the {@code recordRequester == null}
 * guard swallowed the request that would have fixed it. A record morph
 * therefore never resolved and never fetched its record.</p>
 *
 * <p>Legacy called both inline from the morph
 * ({@code ClientProxy.manager.getClient(this.record)} and
 * {@code ServerHandlerRequestRecording.requestRecording(this.record)}); the
 * second helper was a {@code @SideOnly(Side.CLIENT)} static on a server handler,
 * which split source sets forbid — the port already relocated it onto
 * {@link ClientHandlerFramesLoad}, so that is what this points at.</p>
 */
public class RecordMorphClient
{
    private RecordMorphClient()
    {}

    /**
     * Point {@link RecordMorph}'s two client seams at the real client
     * implementations. Plain assignment, so it is idempotent by construction.
     */
    public static void install()
    {
        RecordMorph.recordSource = ClientProxy.manager::getClient;
        RecordMorph.recordRequester = ClientHandlerFramesLoad::requestRecording;
    }

    /** Whether both seams are assigned. */
    public static boolean isInstalled()
    {
        return RecordMorph.recordSource != null && RecordMorph.recordRequester != null;
    }
}
