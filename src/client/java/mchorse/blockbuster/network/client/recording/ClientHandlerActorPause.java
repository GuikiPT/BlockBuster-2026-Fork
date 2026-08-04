package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.network.common.recording.PacketActorPause;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Client handler for {@link PacketActorPause} (roadmap P116) — mirrors a
 * server-side pause/resume onto the client's copy of the playback.
 *
 * <p>1:1 port of 1.12.2 {@code ClientHandlerActorPause}. Two details are
 * legacy's and load-bearing:</p>
 *
 * <ul>
 *   <li>{@code tick}/{@code playing} are written <b>after</b> the
 *   pause/resume call, because {@link RecordPlayer#resume(int)} would
 *   otherwise be free to move the tick itself;</li>
 *   <li>the whole prev-field block ({@code prevX/Y/Z}, the three prev
 *   rotations and {@code prevBodyYaw}) is collapsed onto the current values.
 *   Without it the renderer interpolates from wherever the actor stood before
 *   the seek and the actor visibly slides across the world for one frame.</li>
 * </ul>
 */
public class ClientHandlerActorPause extends ClientMessageHandler<PacketActorPause>
{
    @Override
    public void run(ClientPlayerEntity player, PacketActorPause message)
    {
        Entity entity = player.getWorld().getEntityById(message.id);

        if (!(entity instanceof LivingEntity actor))
        {
            return;
        }

        RecordPlayer playback = EntityUtils.getRecordPlayer(actor);

        if (playback == null)
        {
            return;
        }

        if (message.pause)
        {
            playback.pause();
        }
        else
        {
            playback.resume(message.tick);
        }

        playback.tick = message.tick;
        playback.playing = !message.pause;

        if (playback.record != null)
        {
            Record record = playback.record;

            playback.applyFrame(message.tick - 1, actor, true);

            Frame frame = record.getFrameSafe(message.tick - record.preDelay - 1);

            if (frame != null && frame.hasBodyYaw)
            {
                actor.bodyYaw = frame.bodyYaw;
            }

            actor.lastRenderX = actor.prevX = actor.getX();
            actor.lastRenderY = actor.prevY = actor.getY();
            actor.lastRenderZ = actor.prevZ = actor.getZ();
            actor.prevPitch = actor.getPitch();
            actor.prevYaw = actor.getYaw();
            actor.prevHeadYaw = actor.headYaw;
            actor.prevBodyYaw = actor.bodyYaw;
        }
    }
}
