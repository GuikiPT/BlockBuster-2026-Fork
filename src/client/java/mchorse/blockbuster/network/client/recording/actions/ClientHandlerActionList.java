package mchorse.blockbuster.network.client.recording.actions;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.network.common.recording.actions.PacketActionList;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Delivers the server's record listing to the editor (roadmap P117).
 *
 * <p>The record names are the payload consumed by the recording editor's
 * record list. The editor panel API ({@code addRecords}) lands in S12 P138;
 * until then the list arrives with nothing to populate.</p>
 */
public class ClientHandlerActionList extends ClientMessageHandler<PacketActionList>
{
    @Override
    public void run(ClientPlayerEntity player, PacketActionList message)
    {
        if (BlockbusterClient.panels.recordingEditorPanel != null)
        {
            BlockbusterClient.panels.recordingEditorPanel.addRecords(message.records);
        }
    }
}
