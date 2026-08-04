package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketPlayback;
import mchorse.blockbuster.network.common.recording.PacketRequestFrames;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Client handler for {@link PacketPlayback} (roadmap P116) — starts/stops
 * client-side playback on the tracked entity.
 *
 * <p>On start, if the record is cached (or jar-bundled) it is used directly;
 * otherwise the frames are requested from the server ({@code PacketRequestFrames},
 * P115). On stop, the record player is detached and the local player's camera
 * roll is reset.</p>
 */
public class ClientHandlerPlayback extends ClientMessageHandler<PacketPlayback>
{
    @Override
    public void run(ClientPlayerEntity player, PacketPlayback message)
    {
        Entity entity = this.findEntity(player, message.id);

        if (!(entity instanceof LivingEntity actor))
        {
            return;
        }

        if (message.state)
        {
            Record record = ClientProxy.manager.getClient(message.filename);
            RecordPlayer recordPlayer = EntityUtils.getRecordPlayer(actor);

            if (recordPlayer == null)
            {
                recordPlayer = new RecordPlayer(record, Mode.FRAMES, actor);

                recordPlayer.setReplay(message.replay);

                if (message.realPlayer)
                {
                    recordPlayer.realPlayer();
                }

                EntityUtils.setRecordPlayer(actor, recordPlayer);
            }
            else
            {
                recordPlayer.setReplay(message.replay);
                recordPlayer.record = record;
                recordPlayer.realPlayer = message.realPlayer;
                recordPlayer.tick = 0;
            }

            if (record == null)
            {
                /* The client has no frames for this record — ask the server
                 * for them. The empty RecordPlayer installed above keeps the
                 * actor alive (standing still) until ClientHandlerRequestedFrames
                 * fills record in. Same call shape as
                 * ClientHandlerActorSpawnData's uncached branch. */
                Dispatcher.sendToServer(new PacketRequestFrames(message.id, message.filename));
            }
        }
        else
        {
            EntityUtils.setRecordPlayer(actor, null);

            if (actor == MinecraftClient.getInstance().player)
            {
                /* legacy CameraHandler.resetRoll() — clear accumulated roll */
                CameraHandler.setRoll(0F, 0F);
            }
        }
    }

    /**
     * Entity lookup seam — production is the legacy
     * {@code player.world.getEntityByID(id)}; headless tests override it
     * because a {@code ClientPlayerEntity} cannot be built in the JUnit
     * harness.
     */
    protected Entity findEntity(ClientPlayerEntity player, int id)
    {
        return player.getWorld().getEntityById(id);
    }
}
