package mchorse.blockbuster.network.client.recording.actions;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordingEditorRefresh;
import mchorse.blockbuster.network.common.recording.actions.PacketActions;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Applies a pushed action track to the client record cache (roadmap P117),
 * creating the cache entry if absent, and — when the request asked for it —
 * opens the recording editor on that record (S22 P236, through
 * {@link RecordingEditorRefresh}).
 */
public class ClientHandlerActions extends ClientMessageHandler<PacketActions>
{
    @Override
    public void run(ClientPlayerEntity player, PacketActions message)
    {
        Record record = ClientProxy.manager.records.get(message.filename);

        if (record == null)
        {
            record = new Record(message.filename);
            ClientProxy.manager.records.put(message.filename, record);
        }

        record.actions = message.actions;

        /* S22 P236: the request carried "open the editor on this record" */
        if (message.open)
        {
            RecordingEditorRefresh.selectRecord(record);
        }
    }
}
