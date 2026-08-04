package mchorse.blockbuster.capabilities;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.blockbuster.network.common.structure.PacketStructureList;
import mchorse.blockbuster.network.server.ServerHandlerStructureRequest;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.network.IMessage;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

/**
 * Capability handler (roadmap P112) — 1:1 behavioral port of the 2.7.2 class.
 *
 * <p>On 1.12.2 this class attached the recording capability and ran the login /
 * start-tracking sync. On 1.20.4 the attachment rides the
 * {@code PlayerEntityRecordingMixin} duck, so this class keeps only the two
 * sync hooks:</p>
 *
 * <ul>
 * <li><b>Login</b> ({@code ServerPlayConnectionEvents.JOIN}): resend the
 * structure list ({@link PacketStructureList}, P162), reopen the last edited
 * scene ({@link PacketSceneCast} in its silent {@code open(false)} form, S11)
 * and sync playing audio (S16) — all three in the legacy order.</li>
 * <li><b>Start tracking</b> ({@code EntityTrackingEvents.START_TRACKING}): when
 * the tracked living entity carries a {@link RecordPlayer}, push its record
 * frames to the player via {@link RecordUtils#sendRequestedRecord} (the actual
 * frame packet is P115).</li>
 * </ul>
 */
public class CapabilityHandler
{
    /** Legacy {@code RECORDING_CAP} resource key (kept for parity / diff-ability). */
    public static final Identifier RECORDING_CAP = new Identifier("blockbuster", "recording_capability");

    /**
     * Headless-test seam: the sink {@link #playerLogsIn} pushes its join
     * payloads through. Defaults to the real {@link Dispatcher} (same idiom as
     * {@code CommonProxy.saveRootOverride} / {@code Config.serverSender}); a
     * test swaps it to capture what a joining player would receive without a
     * live server connection.
     */
    public static BiConsumer<IMessage, ServerPlayerEntity> packetSender = Dispatcher::sendTo;

    public static void register()
    {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> playerLogsIn(handler.getPlayer()));

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> playerStartsTracking(trackedEntity, player));
    }

    /**
     * When player logs in, send him his server counterpart's values.
     */
    public static void playerLogsIn(ServerPlayerEntity player)
    {
        /* P162: push the structure-name list. Legacy sent this first and
         * unconditionally (before it ever touched the capability), so it stays
         * outside the recording guard. */
        packetSender.accept(new PacketStructureList(ServerHandlerStructureRequest.getAllStructures()), player);

        IRecording recording = Recording.get(player);

        /* S11 (P131.1): reopen the last edited scene. The silent open(false)
         * form — the client updates the panel's state without popping the
         * dashboard (see ClientHandlerSceneCast). */
        if (recording != null && recording.getLastScene() != null)
        {
            Scene scene = CommonProxy.scenes.get(recording.getLastScene(), player == null ? null : player.getWorld());

            if (scene != null)
            {
                packetSender.accept(new PacketSceneCast(new SceneLocation(scene)).open(false), player);
            }
        }

        /* S16 P189: a player who joins mid-playback must receive the current
         * audio state (converted to a *_SET tick) for every loaded scene. */
        for (Scene scene : CommonProxy.scenes.getScenes().values())
        {
            scene.syncAudio(player);
        }
    }

    /**
     * When a player starts tracking an actor, the server has to send the
     * actor's record frames to that player.
     */
    public static void playerStartsTracking(Entity target, ServerPlayerEntity player)
    {
        if (target instanceof LivingEntity living)
        {
            RecordPlayer playback = EntityUtils.getRecordPlayer(living);

            if (playback != null && playback.record != null)
            {
                RecordUtils.sendRequestedRecord(target.getId(), playback.record.filename, player);
            }
        }
    }
}
