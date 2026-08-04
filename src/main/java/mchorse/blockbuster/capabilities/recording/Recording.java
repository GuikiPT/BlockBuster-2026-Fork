package mchorse.blockbuster.capabilities.recording;

import java.util.HashMap;
import java.util.Map;

import mchorse.blockbuster.recording.RecordPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Default implementation of {@link IRecording} (roadmap P112) — 1:1 port of the
 * 2.7.2 class.
 *
 * <p>{@link #get(PlayerEntity)} replaces legacy's
 * {@code player.getCapability(RecordingProvider.RECORDING, null)} — it casts the
 * player through the {@link RecordingHolder} duck interface installed by the
 * {@code PlayerEntityRecordingMixin}, so every call site reads exactly like
 * 1.12.2.</p>
 */
public class Recording implements IRecording
{
    /**
     * In-memory fallback (P115/P117 seam): when a player is not a
     * {@link RecordingHolder} (e.g. headless tests, or a null player standing
     * in for the resend cache), {@link #get(PlayerEntity)} routes here so the
     * frame/action networking + {@code RecordUtils} resend state machine are
     * exercisable ahead of / independent of the live attachment.
     */
    private static final Map<PlayerEntity, Recording> FALLBACK = new HashMap<>();

    public String lastScene = "";
    public Map<String, ItemInfo> recordings = new HashMap<String, ItemInfo>();
    public BlockPos teleportPos;
    public RecordPlayer player;
    public boolean fakePlayer;

    /**
     * Legacy {@code Recording.get(EntityPlayer)}: resolves the per-player
     * recording capability. On 1.20.4 the instance rides the
     * {@link RecordingHolder} duck interface (mixin-attached, lazily created).
     * Non-holder players (headless tests, null) fall back to a stable
     * in-memory instance keyed by identity.
     */
    public static IRecording get(PlayerEntity player)
    {
        if (player instanceof RecordingHolder)
        {
            return ((RecordingHolder) player).blockbuster$getRecording();
        }

        return FALLBACK.computeIfAbsent(player, p -> new Recording());
    }

    /** Headless-test seam: drop the in-memory fallback state. */
    public static void resetFallback()
    {
        FALLBACK.clear();
    }

    @Override
    public String getLastScene()
    {
        return this.lastScene;
    }

    @Override
    public void setLastScene(String scene)
    {
        if (scene == null)
        {
            return;
        }

        this.lastScene = scene;
    }

    @Override
    public boolean hasRecording(String filename)
    {
        return this.recordings.containsKey(filename);
    }

    @Override
    public long recordingTimestamp(String filename)
    {
        return this.recordings.get(filename).timestamp;
    }

    @Override
    public void addRecording(String filename, long timestamp)
    {
        if (this.hasRecording(filename))
        {
            this.updateRecordingTimestamp(filename, timestamp);
        }
        else
        {
            this.recordings.put(filename, new ItemInfo(filename, timestamp));
        }
    }

    @Override
    public void removeRecording(String filename)
    {
        this.recordings.remove(filename);
    }

    @Override
    public void removeRecordings()
    {
        this.recordings.clear();
    }

    @Override
    public void updateRecordingTimestamp(String filename, long timestamp)
    {
        if (this.hasRecording(filename))
        {
            this.recordings.get(filename).timestamp = timestamp;
        }
    }

    @Override
    public void setLastTeleportedBlockPos(BlockPos pos)
    {
        this.teleportPos = pos;
    }

    @Override
    public BlockPos getLastTeleportedBlockPos()
    {
        return this.teleportPos;
    }

    @Override
    public void setRecordPlayer(RecordPlayer player)
    {
        this.player = player;
    }

    @Override
    public RecordPlayer getRecordPlayer()
    {
        return this.player;
    }

    @Override
    public boolean isFakePlayer()
    {
        return this.fakePlayer;
    }

    @Override
    public void setFakePlayer(boolean fakePlayer)
    {
        this.fakePlayer = fakePlayer;
    }

    /**
     * Item information class
     *
     * Instance of this class is responsible for storing information about a
     * file item like camera profile or recording with timestamp of when
     * it was changed.
     */
    public static class ItemInfo
    {
        public String filename;
        public long timestamp;

        public ItemInfo()
        {
            this("", -1);
        }

        public ItemInfo(String filename, long timestamp)
        {
            this.filename = filename;
            this.timestamp = timestamp;
        }
    }
}
