package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Morph action
 *
 * This action is responsible for changing the model and skin of actor during
 * the playback. This action is submitted when player morphs with Metamorph's
 * API.
 *
 * <p>Port note (P167): the S9-era raw-NBT carrier stub is gone. Now that the
 * bundled Metamorph {@code MorphManager} dispatch (P49) is complete the morph
 * is held as a real {@link AbstractMorph}, discriminated on the {@code Name}
 * tag and reconstructed through the total {@code morphFromNBT} reader (an
 * unknown name yields {@code null} — a warning + placeholder, never a crash).
 * {@code apply}/{@code applyWithForce}/{@code applyWithOffset} mirror the
 * 1.12.2 player-vs-actor split verbatim (see legacy
 * {@code recording/actions/MorphAction.java}).</p>
 */
public class MorphAction extends Action
{
    public AbstractMorph morph;

    public MorphAction()
    {}

    public MorphAction(AbstractMorph morph)
    {
        this.morph = morph;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        AbstractMorph morph = MorphUtils.copy(this.morph);

        if (actor instanceof PlayerEntity)
        {
            MorphAPI.morph((PlayerEntity) actor, morph, true);
        }
        else if (actor instanceof EntityActor)
        {
            EntityActor act = (EntityActor) actor;

            act.morph(morph, false);

            if (!act.getWorld().isClient())
            {
                act.notifyPlayers();
            }
        }
    }

    @Override
    public void applyWithForce(LivingEntity actor)
    {
        AbstractMorph morph = MorphUtils.copy(this.morph);

        if (actor instanceof PlayerEntity)
        {
            MorphAPI.morph((PlayerEntity) actor, morph, true);
        }
        else if (actor instanceof EntityActor)
        {
            EntityActor act = (EntityActor) actor;

            act.morph(morph, true);

            if (!act.getWorld().isClient())
            {
                act.notifyPlayers();
            }
        }
    }

    /**
     * Mid-animation morph switch (scrub/seek path used by P160/P161). Players
     * can't be offset-synced (legacy comment: "Sorry, fake players can't be
     * synced"), so they take a plain morph; actors go through the
     * {@code applyPause}/{@code morphPause} sync path — client applies the pause
     * locally, server also {@code notifyPlayers()} (order matters for tracker
     * consistency).
     */
    public void applyWithOffset(LivingEntity actor, int offset, AbstractMorph previous, int previousOffset, boolean resume)
    {
        AbstractMorph morph = MorphUtils.copy(this.morph);

        /* Sorry, fake players can't be synced */
        if (actor instanceof PlayerEntity)
        {
            MorphAPI.morph((PlayerEntity) actor, morph, true);
        }
        else if (actor instanceof EntityActor)
        {
            EntityActor act = (EntityActor) actor;

            if (act.getWorld().isClient())
            {
                act.applyPause(MorphUtils.copy(morph), offset, MorphUtils.copy(previous), previousOffset, resume);
            }
            else
            {
                act.morphPause(morph, offset, previous, previousOffset, resume);
                act.notifyPlayers();
            }
        }
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);

        this.morph = MorphUtils.morphFromBuf(buf);
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);

        MorphUtils.morphToBuf(buf, this.morph);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.morph = MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Morph"));
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        if (this.morph != null)
        {
            tag.put("Morph", this.morph.toNBT());
        }
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
