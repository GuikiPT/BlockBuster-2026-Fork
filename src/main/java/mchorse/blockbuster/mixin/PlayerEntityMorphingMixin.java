package mchorse.blockbuster.mixin;

import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.capabilities.morphing.MorphingHolder;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * P52 — attaches the per-player {@link IMorphing} capability to every
 * {@code PlayerEntity} (technique ledger: mixin-attached component, no Cardinal
 * dependency; mirrors P112's {@code PlayerEntityRecordingMixin} and BBS's
 * {@code PlayerEntityMorphMixin}).
 *
 * <p>This mixin only adds the {@link MorphingHolder} duck interface and holds
 * the lazily-created instance. NBT persistence lives in the <b>separate</b>
 * {@code PlayerEntityMorphDataMixin} — splitting the interface-adder from the
 * {@code writeCustomDataToNbt}/{@code readCustomDataFromNbt} injections is the
 * BBS-endorsed technique that avoids the world-lock bug documented in
 * {@code bbs_mod/mixin/PlayerEntityMixin.java}.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMorphingMixin implements MorphingHolder
{
    @Unique
    private IMorphing metamorph$morphing;

    @Override
    public IMorphing metamorph$getMorphing()
    {
        if (this.metamorph$morphing == null)
        {
            this.metamorph$morphing = new Morphing();
        }

        return this.metamorph$morphing;
    }
}
