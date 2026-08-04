package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.network.common.recording.PacketSyncTick;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Client handler for {@link PacketSyncTick} (roadmap P116) — resynchronizes a
 * playing actor's tick, re-applying the frame when the actor is paused.
 */
public class ClientHandlerSyncTick extends ClientMessageHandler<PacketSyncTick>
{
    @Override
    public void run(ClientPlayerEntity player, PacketSyncTick message)
    {
        Entity entity = player.getWorld().getEntityById(message.id);

        if (!(entity instanceof LivingEntity actor))
        {
            return;
        }

        RecordPlayer playback = EntityUtils.getRecordPlayer(actor);

        if (playback != null && playback.record != null)
        {
            playback.tick = message.tick;

            if (!playback.playing)
            {
                playback.applyFrame(message.tick - 1, actor, false);
            }
        }
    }
}
