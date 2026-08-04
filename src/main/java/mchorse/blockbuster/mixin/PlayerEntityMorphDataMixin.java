package mchorse.blockbuster.mixin;

import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.MorphingHolder;
import mchorse.metamorph.capabilities.morphing.MorphingStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P52 — persists the per-player {@link IMorphing} capability into the player
 * {@code .dat} (technique ledger). Kept in a <b>separate</b> mixin class from
 * the interface-adding {@code PlayerEntityMorphingMixin}: the BBS
 * {@code PlayerEntityMixin} comment records that keeping the NBT injections
 * apart from the interface-adder avoided a world-lock bug — this split copies
 * that technique.
 *
 * <p>1.12.2 Forge stored the capability under {@code ForgeCaps →
 * "metamorph:morphing_capability"} in the player {@code .dat}. The port writes
 * its own top-level {@code metamorph:morphing_capability} compound with the same
 * inner layout; on read it also honors the legacy {@code ForgeCaps} nesting so
 * old-world player {@code .dat} files migrate their acquired/current morphs
 * (S20 migration). Total reader — a missing/short compound just leaves the
 * default (unmorphed) capability.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMorphDataMixin
{
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void metamorph$writeMorphing(NbtCompound nbt, CallbackInfo info)
    {
        IMorphing morphing = ((MorphingHolder) (Object) this).metamorph$getMorphing();

        NbtCompound tag = new NbtCompound();

        MorphingStorage.writeNBT(morphing, tag);

        nbt.put(MorphingStorage.MORPHING_KEY, tag);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void metamorph$readMorphing(NbtCompound nbt, CallbackInfo info)
    {
        IMorphing morphing = ((MorphingHolder) (Object) this).metamorph$getMorphing();

        NbtCompound tag = null;

        if (nbt.contains(MorphingStorage.MORPHING_KEY, NbtElement.COMPOUND_TYPE))
        {
            tag = nbt.getCompound(MorphingStorage.MORPHING_KEY);
        }
        else if (nbt.contains("ForgeCaps", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound caps = nbt.getCompound("ForgeCaps");

            if (caps.contains(MorphingStorage.MORPHING_KEY, NbtElement.COMPOUND_TYPE))
            {
                tag = caps.getCompound(MorphingStorage.MORPHING_KEY);
            }
        }

        MorphingStorage.readNBT(morphing, tag);
    }
}
