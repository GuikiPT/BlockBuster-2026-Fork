package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordingEditorRefresh;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketFramesLoad;
import mchorse.blockbuster.network.common.recording.PacketRequestRecording;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ClientMessageHandler;
import mchorse.mclib.utils.Consumers;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Client handler frames (roadmap P115).
 *
 * <p>Inserts a record received from the server into the client's record
 * repository. LOAD builds a {@link Record} and caches it and refreshes the open
 * recording editor (S22 P236, through
 * {@link mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordingEditorRefresh});
 * NOCHANGES reuses the cached record; then <b>always</b> consumes a registered callback if the
 * packet carried a callback id — <b>ERROR consumes the callback with a null
 * record</b> (GUI flows rely on that to clear busy states). The
 * {@code Consumers<Record>} registry + {@link #registerConsumer(Consumer)} is
 * the RPC seam P116's {@code requestRecording} round-trips through.</p>
 */
public class ClientHandlerFramesLoad extends ClientMessageHandler<PacketFramesLoad>
{
    private static final Consumers<Record> consumers = new Consumers<Record>();

    @Override
    public void run(ClientPlayerEntity player, PacketFramesLoad message)
    {
        Record record = null;

        if (message.getState() == PacketFramesLoad.State.LOAD)
        {
            record = new Record(message.filename);
            record.frames = message.frames;
            record.preDelay = message.preDelay;
            record.postDelay = message.postDelay;

            ClientProxy.manager.records.put(message.filename, record);

            /* S22 P236: refresh the open editor's pre/post delay if this is the
             * record it is editing (the filename check is reselectRecord's) */
            RecordingEditorRefresh.reselectRecord(record);
        }
        else if (message.getState() == PacketFramesLoad.State.NOCHANGES)
        {
            record = ClientProxy.manager.records.get(message.filename);
        }

        if (message.getCallbackID().isPresent())
        {
            consumers.consume(message.getCallbackID().get(), record);
        }
    }

    public static int registerConsumer(Consumer<Record> consumer)
    {
        return consumers.register(consumer);
    }

    /**
     * Request a recording from the server (roadmap P116).
     *
     * <p>Legacy kept this as a {@code @SideOnly(Side.CLIENT)} static on
     * {@code ServerHandlerRequestRecording}; split source sets forbid a
     * main-set class touching the client-only consumer registry, so it lives
     * here — the same relocation {@code sendFramesToServer} made onto
     * {@link ClientHandlerFramesOverwrite}.</p>
     *
     * @param consumer invoked with the record once the server's
     *                 {@code PacketFramesLoad} reply arrives, or with
     *                 {@code null} when the server answered ERROR. Pass
     *                 {@code null} for a fire-and-forget request (the record
     *                 still lands in the client cache).
     */
    public static void requestRecording(String filename, @Nullable Consumer<Record> consumer)
    {
        if (consumer != null)
        {
            int id = registerConsumer(consumer);

            Dispatcher.sendToServer(new PacketRequestRecording(filename, id));
        }
        else
        {
            Dispatcher.sendToServer(new PacketRequestRecording(filename));
        }
    }

    /** Fire-and-forget overload of {@link #requestRecording(String, Consumer)}. */
    public static void requestRecording(String filename)
    {
        requestRecording(filename, null);
    }
}
