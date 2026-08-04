package mchorse.blockbuster.network.client;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketActorSpawnData;
import mchorse.blockbuster.network.common.recording.PacketRequestFrames;
import mchorse.blockbuster.network.common.recording.actions.PacketRequestAction;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

/**
 * Client handler for {@link PacketActorSpawnData} (roadmap P119.2) — 1:1 port
 * of legacy {@code EntityActor.readSpawnData}.
 *
 * <p>Two behaviors here are the point of the whole packet:</p>
 *
 * <ul>
 *   <li><b>The record may not be cached yet.</b> When it is, playback is built
 *   against it and {@code applyPreviousMorph} immediately puts the actor in the
 *   morph it should be wearing at {@code tick} — {@code FORCE} while playing,
 *   {@code PAUSE} while paused, which is the difference between an animated
 *   morph resuming mid-animation and restarting. When it is not, an empty
 *   playback is installed and the actions + frames are requested from the
 *   server; the actor stands still until they arrive rather than not existing.</li>
 *   <li><b>An existing playback is never replaced</b> — only its
 *   {@code tick}/{@code playing} are refreshed. A re-track of an actor already
 *   playing must not restart it.</li>
 * </ul>
 */
public class ClientHandlerActorSpawnData extends ClientMessageHandler<PacketActorSpawnData>
{
    @Override
    public void run(ClientPlayerEntity player, PacketActorSpawnData message)
    {
        if (player == null || player.getWorld() == null)
        {
            return;
        }

        Entity entity = player.getWorld().getEntityById(message.id);

        if (!(entity instanceof EntityActor actor))
        {
            return;
        }

        actor.morph.setDirect(message.morph);
        actor.invisible = message.invisible;
        actor.enableBurning = message.enableBurning;
        actor.noClip = message.noClip;

        if (message.hasPlayback)
        {
            Replay replay = null;

            if (message.replayMorph != null)
            {
                replay = new Replay();
                replay.morph = message.replayMorph;
            }

            if (actor.playback == null)
            {
                Record record = ClientProxy.manager.getClient(message.filename);

                if (record != null)
                {
                    actor.playback = new RecordPlayer(record, Mode.FRAMES, actor);

                    record.applyPreviousMorph(actor, replay, message.tick - record.preDelay,
                        message.playing ? Record.MorphType.FORCE : Record.MorphType.PAUSE);
                }
                else
                {
                    actor.playback = new RecordPlayer(null, Mode.FRAMES, actor);

                    Dispatcher.sendToServer(new PacketRequestAction(message.filename, false));
                    Dispatcher.sendToServer(new PacketRequestFrames(actor.getId(), message.filename));
                }
            }

            actor.playback.tick = message.tick;
            actor.playback.playing = message.playing;
        }

        actor.setInvulnerable(message.invulnerable);
        actor.renderLast = message.renderLast;
    }
}
