package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordingEditorRefresh;
import mchorse.blockbuster.network.common.recording.PacketRequestedFrames;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Client handler requested frames (roadmap P115).
 *
 * <p>Saves received frames into the client record manager, injects the received
 * record into the actor identified by the trailing entity id, and refreshes the
 * open recording editor (S22 P236, through {@link RecordingEditorRefresh}).</p>
 */
public class ClientHandlerRequestedFrames extends ClientMessageHandler<PacketRequestedFrames>
{
    @Override
    public void run(ClientPlayerEntity player, PacketRequestedFrames message)
    {
        Record record = new Record(message.filename);
        record.frames = message.frames;
        record.preDelay = message.preDelay;
        record.postDelay = message.postDelay;

        ClientProxy.manager.records.put(record.filename, record);

        RecordPlayer playback = this.findPlayback(player, message.id);

        if (playback != null)
        {
            playback.record = record;
        }

        /* S22 P236: refresh the open editor's pre/post delay if this is the
         * record it is editing (the filename check is reselectRecord's) */
        RecordingEditorRefresh.reselectRecord(record);
    }

    /**
     * The world lookup for the actor this record belongs to, isolated so the
     * cache insert + editor refresh are exercisable without a live client world
     * (same protected-seam convention as {@code ServerHandlerRequestRecording
     * .sendRecord}). Legacy cast the entity straight to {@code
     * EntityLivingBase}; the {@code instanceof} keeps the reader total.
     */
    protected RecordPlayer findPlayback(ClientPlayerEntity player, int id)
    {
        Entity entity = player.getWorld().getEntityById(id);

        return entity instanceof LivingEntity ? EntityUtils.getRecordPlayer((LivingEntity) entity) : null;
    }
}
