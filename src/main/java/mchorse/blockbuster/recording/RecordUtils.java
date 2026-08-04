package mchorse.blockbuster.recording;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketApplyFrame;
import mchorse.blockbuster.network.common.recording.PacketFramesLoad;
import mchorse.blockbuster.network.common.recording.PacketRequestedFrames;
import mchorse.blockbuster.network.common.recording.PacketUnloadFrames;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.utils.BlockbusterPaths;
import net.minecraft.entity.player.PlayerEntity;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities methods mostly to be used with recording code (roadmap P103) —
 * 1:1 port of legacy {@code recording/RecordUtils} + the {@code recording/
 * Utils} path halves (which live centrally in {@link BlockbusterPaths}, P7).
 *
 * <p>The pieces P103 originally left open are all wired: {@link
 * #playerNeedsAction(String, PlayerEntity)} and the per-player resend cache
 * ride the {@code IRecording} attachment (<b>P112</b>), {@link #sendRecordTo}
 * and {@link #sendRequestedRecord} do the frame networking (<b>P115</b>), and
 * {@link #unloadRecord(Record)} performs the client invalidation
 * ({@code PacketUnloadFrames} + cache drop).</p>
 */
public class RecordUtils
{
    /**
     * String version of {@link #broadcastMessage(Text)}
     */
    public static void broadcastMessage(String message)
    {
        broadcastMessage(Text.literal(message));
    }

    /**
     * I18n formatting version of {@link #broadcastMessage(Text)}
     */
    public static void broadcastMessage(String string, Object... args)
    {
        broadcastMessage(Text.translatable(string, args));
    }

    /**
     * Send given message to everyone on the server, to everyone.
     *
     * Invoke this method only on the server side.
     */
    public static void broadcastMessage(Text message)
    {
        for (ServerPlayerEntity player : getServerPlayers())
        {
            player.sendMessage(message);
        }
    }

    /**
     * Send given error to everyone on the server, to everyone.
     *
     * Invoke this method only on the server side.
     */
    public static void broadcastError(String string, Object... objects)
    {
        for (ServerPlayerEntity player : getServerPlayers())
        {
            Blockbuster.l10n.error(player, string, objects);
        }
    }

    /**
     * Send given error to everyone on the server, to everyone.
     *
     * Invoke this method only on the server side.
     */
    public static void broadcastInfo(String string, Object... objects)
    {
        for (ServerPlayerEntity player : getServerPlayers())
        {
            Blockbuster.l10n.info(player, string, objects);
        }
    }

    /**
     * Legacy {@code ForgeUtils.getServerPlayers()} — empty when no server is
     * bound (headless tests).
     */
    private static List<ServerPlayerEntity> getServerPlayers()
    {
        if (CommonProxy.server == null)
        {
            return new ArrayList<>();
        }

        return CommonProxy.server.getPlayerManager().getPlayerList();
    }

    /**
     * Checks whether player recording exists
     */
    public static boolean isReplayExists(String filename)
    {
        return replayFile(filename).exists() || CommonProxy.manager.records.containsKey(filename);
    }

    /**
     * Get path to replay file (located in current world save's folder)
     */
    public static File replayFile(String filename)
    {
        return BlockbusterPaths.records(CommonProxy.saveRoot(), filename);
    }

    /**
     * This method gets a record that has been saved in the mod's jar file
     * @return An {@link InputStream} object or null if no resource with this name is found
     */
    public static InputStream getLocalReplay(String filename)
    {
        return RecordUtils.class.getResourceAsStream(BlockbusterPaths.builtinRecordResource(filename));
    }

    /**
     * Get list of all available replays
     */
    public static List<String> getReplays()
    {
        return BlockbusterPaths.records(CommonProxy.saveRoot());
    }

    /**
     * Get list of all available replays' backup iterations. Matches on
     * {@code startsWith(replay)} + a {@code .dat~} substring, exactly like
     * legacy — {@code foo} also matches {@code foobar.dat~1}; the rename
     * migration (P111) relies on this.
     */
    public static List<String> getReplayIterations(String replay)
    {
        List<String> list = new ArrayList<String>();
        Path root = CommonProxy.saveRoot();

        if (root == null)
        {
            return list;
        }

        File replays = BlockbusterPaths.recordsFolder(root);
        File[] files = replays.listFiles();

        if (files == null)
        {
            return list;
        }

        for (File file : files)
        {
            String name = file.getName();

            if (file.isFile() && name.startsWith(replay) && name.contains(".dat~"))
            {
                list.add(name.substring(name.indexOf("~") + 1));
            }
        }

        return list;
    }

    /* sendRequestedRecord (the PacketRequestedFrames pairing used by
     * StartTracking) is P115 (below); its IRecording gate
     * ({@link #playerNeedsAction}) is ported here (P112). */

    /**
     * Checks whether given player needs a new action, meaning, he has an older
     * version of given named action or he doesn't have this action at all
     * (roadmap P112 — the {@link IRecording} per-player resend cache).
     *
     * <p>Order is load-bearing: the jar-bundled short-circuit runs <b>before</b>
     * the fake-player check. This method is <b>not</b> a pure predicate — it
     * upserts the recording timestamp as a side effect.</p>
     */
    public static boolean playerNeedsAction(String filename, PlayerEntity player)
    {
        if (RecordUtils.getLocalReplay(filename) != null)
        {
            return false;
        }

        IRecording recording = Recording.get(player);

        if (recording == null || recording.isFakePlayer())
        {
            return false;
        }

        return playerNeedsAction(recording, filename, replayFile(filename).lastModified());
    }

    /**
     * Pure-logic core of {@link #playerNeedsAction(String, PlayerEntity)} (the
     * jar-bundled short-circuit and fake-player gate already applied), split
     * out so the resend state-machine can be driven headlessly with a mock
     * {@link IRecording}. Mutates the capability (timestamp upsert) exactly
     * like legacy.
     */
    public static boolean playerNeedsAction(IRecording recording, String filename, long time)
    {
        boolean has = recording.hasRecording(filename);

        if (has && time > recording.recordingTimestamp(filename))
        {
            recording.updateRecordingTimestamp(filename, time);

            return true;
        }

        if (!has)
        {
            recording.addRecording(filename, time);
        }

        return !has;
    }

    /**
     * Send record frames to given player from the server (roadmap P103/P115).
     * The heavy {@code PacketFramesLoad} rides the P25 chunked transport
     * transparently (frame payloads exceed the serverbound cap).
     */
    public static void sendRecordTo(String filename, ServerPlayerEntity player)
    {
        sendRecordTo(filename, player, -1);
    }

    /**
     * Send record frames to given player from the server.
     *
     * @param callbackID the id of the callback that should be executed on the
     *                   client; -1 if no callback was created/should execute.
     */
    public static void sendRecordTo(String filename, ServerPlayerEntity player, int callbackID)
    {
        if (!playerNeedsAction(filename, player))
        {
            PacketFramesLoad packet = callbackID == -1 ? new PacketFramesLoad(filename, PacketFramesLoad.State.NOCHANGES) :
                                      new PacketFramesLoad(filename, PacketFramesLoad.State.NOCHANGES, callbackID);

            Dispatcher.sendTo(packet, player);

            return;
        }

        RecordManager manager = CommonProxy.manager;
        Record record = manager.records.get(filename);

        if (record == null)
        {
            try
            {
                record = new Record(filename);
                record.load(replayFile(filename));

                manager.records.put(filename, record);
            }
            catch (FileNotFoundException e)
            {
                Blockbuster.l10n.error(player, "recording.not_found", filename);
                record = null;
            }
            catch (Exception e)
            {
                Blockbuster.l10n.error(player, "recording.read", filename);
                e.printStackTrace();
                record = null;
            }
        }

        if (record != null)
        {
            record.resetUnload();

            PacketFramesLoad packet = callbackID == -1 ? new PacketFramesLoad(filename, record.preDelay, record.postDelay, record.frames) :
                                      new PacketFramesLoad(filename, record.preDelay, record.postDelay, record.frames, callbackID);

            Dispatcher.sendTo(packet, player);
        }
        else
        {
            PacketFramesLoad packet = callbackID == -1 ? new PacketFramesLoad(filename, PacketFramesLoad.State.ERROR) :
                                      new PacketFramesLoad(filename, PacketFramesLoad.State.ERROR, callbackID);

            Dispatcher.sendTo(packet, player);
        }
    }

    /**
     * Send requested frames (for an actor) to given player from the server
     * (roadmap P103/P115). The {@link #playerNeedsAction} resend gate (P112) is
     * shared with {@link #sendRecordTo}.
     */
    public static void sendRequestedRecord(int id, String filename, ServerPlayerEntity player)
    {
        Record record = CommonProxy.manager.records.get(filename);

        if (playerNeedsAction(filename, player) && record != null)
        {
            record.resetUnload();

            Dispatcher.sendTo(new PacketRequestedFrames(id, record.filename, record.preDelay, record.postDelay, record.frames), player);
        }
        else if (record == null)
        {
            Blockbuster.l10n.error(player, "recording.not_found", filename);
        }
    }

    /**
     * Unload given record. Sends every online player who has the record an
     * {@code PacketUnloadFrames} and drops it from their {@link IRecording}
     * map (roadmap P103/P112/P115).
     */
    public static void unloadRecord(Record record)
    {
        String filename = record.filename;

        for (ServerPlayerEntity player : getServerPlayers())
        {
            IRecording recording = Recording.get(player);

            if (recording != null && recording.hasRecording(filename))
            {
                recording.removeRecording(filename);

                Dispatcher.sendTo(new PacketUnloadFrames(filename), player);
            }
        }
    }

    /* records are saved on the server side */

    public static boolean saveRecord(Record record) throws IOException
    {
        return saveRecord(record, true);
    }

    public static boolean saveRecord(Record record, boolean unload) throws IOException
    {
        return saveRecord(record, true, unload);
    }

    /**
     * Persist a record to {@code <world>/blockbuster/records/<name>.dat}.
     *
     * <p><b>P284:</b> returns whether the write happened. {@code false} means
     * {@link Record#acceptSave(java.io.File, String, boolean)} refused a
     * zero-frame record over a recording that has frames, and nothing on disk
     * was touched (the {@code .dat~N} chain was not rotated either). The dirty
     * flag is still cleared on a refusal — deliberately: retrying the same empty
     * record would refuse again on every subsequent flush, and the point of the
     * guard is that this edit does <b>not</b> reach the disk.</p>
     */
    public static boolean saveRecord(Record record, boolean savePast, boolean unload) throws IOException
    {
        record.dirty = false;

        boolean saved = record.save(replayFile(record.filename), savePast);

        if (unload)
        {
            unloadRecord(record);
        }

        return saved;
    }

    public static void dirtyRecord(Record record)
    {
        record.dirty = true;

        unloadRecord(record);
    }

    /**
     * This method filters 360 degrees flips in the given frame list in the given rotation channel.
     * It does not modify the original list but returns a new list of frame copies.
     * @param frames the frames to filter
     * @param from from tick
     * @param to to tick (this tick will also be filtered)
     * @param channel the rotation channel of the frames to filter
     * @return the filtered frames. Returns an empty list if not enough frames are present to filter.
     */
    public static List<Frame> discontinuityEulerFilter(List<Frame> frames, int from, int to, Frame.RotationChannel channel)
    {
        List<Frame> filteredFrames = new ArrayList<>();

        if (to - from + 1 < 2) return filteredFrames;

        for (int i = from; i < frames.size() && i <= to; i++)
        {
            if (i == 0)
            {
                filteredFrames.add(frames.get(i));

                continue;
            }

            Frame filteredFrame = frames.get(i).copy();
            Frame prevFrame = frames.get(i - 1);

            if (i > from)
            {
                prevFrame = filteredFrames.get(i - from - 1);
            }

            switch (channel)
            {
                case BODY_YAW:
                    float prev = (float) Math.toRadians(prevFrame.bodyYaw);
                    float current = (float) Math.toRadians(frames.get(i).bodyYaw);
                    filteredFrame.bodyYaw = (float) Math.toDegrees(MathUtils.filterFlips(prev, current));

                    break;
                case HEAD_PITCH:
                    prev = (float) Math.toRadians(prevFrame.pitch);
                    current = (float) Math.toRadians(frames.get(i).pitch);
                    filteredFrame.pitch = (float) Math.toDegrees(MathUtils.filterFlips(prev, current));

                    break;
                case HEAD_YAW:
                    /* filter both yawHead and yaw... I hope that is correct, Minecraft is weird */
                    prev = (float) Math.toRadians(prevFrame.yawHead);
                    current = (float) Math.toRadians(frames.get(i).yawHead);
                    filteredFrame.yawHead = (float) Math.toDegrees(MathUtils.filterFlips(prev, current));

                    prev = (float) Math.toRadians(prevFrame.yaw);
                    current = (float) Math.toRadians(frames.get(i).yaw);
                    filteredFrame.yaw = (float) Math.toDegrees(MathUtils.filterFlips(prev, current));

                    break;
            }

            filteredFrames.add(filteredFrame);
        }

        return filteredFrames;
    }

    /**
     * This method applies a frame at the given tick on the given entity
     * and synchronises with all players depending on which side this method
     * has been executed on (P115).
     */
    public static void applyFrameOnEntity(LivingEntity entity, Record record, int tick)
    {
        /* Total: legacy's clamp yields -1 for an empty/truncated recording
         * (clamp(0, 0, -1) == -1) and then IndexOutOfBounds out of a
         * user-clickable button. Nothing to apply — return. */
        if (record == null || record.frames.isEmpty())
        {
            return;
        }

        tick = MathUtils.clamp(tick, 0, record.frames.size() - 1);

        Frame frame = record.frames.get(tick);

        frame.apply(entity, true);

        /* Frame does not apply bodyYaw, EntityActor.updateDistance() does... TODO refactor this*/
        entity.bodyYaw = frame.bodyYaw;

        syncFrameApply(frame, entity.getId(), entity.getWorld().isClient);
    }

    /**
     * The {@code PacketApplyFrame} sync half of
     * {@link #applyFrameOnEntity(LivingEntity, Record, int)} (P115), split out
     * as the headless test seam — {@code applyFrameOnEntity} itself needs a
     * {@code LivingEntity} inside a live {@code World} (see
     * {@code PlaybackEndToEndTest}'s note on that limitation), while this half
     * only needs the frame, the entity id and the side.
     *
     * <p>Legacy control flow verbatim: a client-side apply is relayed to the
     * server (which re-applies it, then {@code sendToAll}s it via
     * {@code ServerHandlerApplyFrame} — that's why there is no local
     * echo-suppression), a server-side apply is fanned out to every connected
     * player directly. 1.12 looped {@code ForgeUtils.getServerPlayers()} rather
     * than calling {@code Dispatcher.sendToAll}; the port keeps the loop over
     * the local {@code getServerPlayers()} helper, which is also what makes it
     * a silent no-op with no bound server (headless).</p>
     */
    public static void syncFrameApply(Frame frame, int entityID, boolean remote)
    {
        PacketApplyFrame packet = new PacketApplyFrame(frame, entityID);

        if (remote)
        {
            /* send to server which will also sync it with all other players */
            Dispatcher.sendToServer(packet);
        }
        else
        {
            /* already on server - sync with all players */
            for (ServerPlayerEntity player : getServerPlayers())
            {
                Dispatcher.sendTo(packet, player);
            }
        }
    }
}
