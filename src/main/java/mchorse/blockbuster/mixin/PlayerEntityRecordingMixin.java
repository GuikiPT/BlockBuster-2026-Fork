package mchorse.blockbuster.mixin;

import mchorse.blockbuster.capabilities.recording.IRecording;
import mchorse.blockbuster.capabilities.recording.Recording;
import mchorse.blockbuster.capabilities.recording.RecordingHolder;
import mchorse.blockbuster.capabilities.recording.RecordingStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P112 — attaches the per-player {@link IRecording} capability to every
 * {@code PlayerEntity} (technique ledger: mixin-attached component, no Cardinal
 * dependency) and persists its single serialized field.
 *
 * <p>1.12.2 Forge stored the capability under {@code ForgeCaps →
 * "blockbuster:recording_capability" → {Scene: "..."}} in the player .dat.
 * The port writes its own top-level {@code blockbuster:recording_capability}
 * compound with the same inner {@code Scene} string key; on read it also honors
 * the legacy {@code ForgeCaps} nesting so old-world player .dat files migrate
 * their last scene (total reader — a missing/short compound just leaves the
 * default empty scene).</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityRecordingMixin implements RecordingHolder
{
    /** Legacy {@code CapabilityHandler.RECORDING_CAP} key. */
    @Unique
    private static final String BLOCKBUSTER$RECORDING_KEY = "blockbuster:recording_capability";

    @Unique
    private IRecording blockbuster$recording;

    @Override
    public IRecording blockbuster$getRecording()
    {
        if (this.blockbuster$recording == null)
        {
            this.blockbuster$recording = new Recording();
        }

        return this.blockbuster$recording;
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void blockbuster$writeRecording(NbtCompound nbt, CallbackInfo info)
    {
        NbtCompound tag = new NbtCompound();

        RecordingStorage.writeNBT(this.blockbuster$getRecording(), tag);

        nbt.put(BLOCKBUSTER$RECORDING_KEY, tag);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void blockbuster$readRecording(NbtCompound nbt, CallbackInfo info)
    {
        NbtCompound tag = null;

        if (nbt.contains(BLOCKBUSTER$RECORDING_KEY, NbtElement.COMPOUND_TYPE))
        {
            tag = nbt.getCompound(BLOCKBUSTER$RECORDING_KEY);
        }
        else if (nbt.contains("ForgeCaps", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound caps = nbt.getCompound("ForgeCaps");

            if (caps.contains(BLOCKBUSTER$RECORDING_KEY, NbtElement.COMPOUND_TYPE))
            {
                tag = caps.getCompound(BLOCKBUSTER$RECORDING_KEY);
            }
        }

        RecordingStorage.readNBT(this.blockbuster$getRecording(), tag);
    }
}
