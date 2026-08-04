package mchorse.blockbuster.capabilities.recording;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * Recording capability persistence (roadmap P112) — 1:1 port of the 2.7.2
 * {@code RecordingStorage}.
 *
 * <p>Only the last edited scene name is persisted. The recordings map,
 * teleport pos, record player and fake-player flag are deliberately
 * session-only: when a client re-joins it has none of that state except the
 * last camera profile/scene, so the rest is never stored (this is the record
 * re-send dedup design — clients must re-receive records after relog).</p>
 *
 * <p>The NBT key is {@code "Scene"} (capitalized) — a player-data parity
 * requirement. Read guards on {@code TAG_STRING} exactly like legacy.</p>
 */
public class RecordingStorage
{
    /**
     * Legacy {@code writeNBT}: writes the sub-compound content (just
     * {@code Scene}) into the given tag.
     */
    public static void writeNBT(IRecording instance, NbtCompound tag)
    {
        tag.putString("Scene", instance.getLastScene());
    }

    /**
     * Legacy {@code readNBT}: restores {@code lastScene} from the sub-compound
     * when the string key is present.
     */
    public static void readNBT(IRecording instance, NbtCompound tag)
    {
        if (tag != null && tag.contains("Scene", NbtElement.STRING_TYPE))
        {
            instance.setLastScene(tag.getString("Scene"));
        }
    }
}
