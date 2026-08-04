package mchorse.blockbuster.capabilities.recording;

import mchorse.blockbuster.recording.RecordPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Recording capability (roadmap P112) — 1:1 port of the 2.7.2 interface.
 *
 * This capability is responsible for tracking a player's server-side recording
 * resources such as loaded records (and data related to tracking the changes of
 * these resources).
 *
 * <p>On 1.20.4 there is no Forge capability system; the instance is attached to
 * every {@code PlayerEntity} through the {@code RecordingHolder} duck interface
 * (mixin), and resolved via {@link Recording#get}. Only the {@code lastScene}
 * string is persisted (see {@code RecordingHolder} NBT hooks); everything else
 * is deliberately session-only.</p>
 */
public interface IRecording
{
    /**
     * Get last edited scene
     */
    public String getLastScene();

    /**
     * Set last edited scene
     */
    public void setLastScene(String scene);

    /**
     * Does player has loaded recording?
     */
    public boolean hasRecording(String filename);

    /**
     * What is the last time given recording was updated?
     */
    public long recordingTimestamp(String filename);

    /**
     * Add a recording
     */
    public void addRecording(String filename, long timestamp);

    /**
     * Remove a recording
     */
    public void removeRecording(String filename);

    /**
     * Remove all recordings
     */
    public void removeRecordings();

    /**
     * Update given recording's timestamp
     */
    public void updateRecordingTimestamp(String filename, long timestamp);

    /**
     * Set last teleported block position
     */
    public void setLastTeleportedBlockPos(BlockPos pos);

    /**
     * Get last teleported block position
     */
    public BlockPos getLastTeleportedBlockPos();

    /**
     * Set record player which will animate this player
     */
    public void setRecordPlayer(RecordPlayer player);

    /**
     * Get the record player which animates this player
     */
    public RecordPlayer getRecordPlayer();

    /**
     * Whether this player is fake
     */
    public boolean isFakePlayer();

    /**
     * Set fake player
     */
    public void setFakePlayer(boolean fakePlayer);
}
