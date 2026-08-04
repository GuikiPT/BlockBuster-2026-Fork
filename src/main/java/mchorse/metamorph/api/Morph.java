package mchorse.metamorph.api;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;

import java.util.Objects;

/**
 * Morph container (roadmap P48).
 *
 * <p>{@link #set(AbstractMorph)} returning {@code false} means "merged, old
 * instance kept" — Blockbuster actors and body parts rely on this for seamless
 * animation transitions; do not invert the boolean's meaning. {@code
 * afterMerge} is called <b>on the incoming morph with the OLD morph as
 * argument</b>, and <b>only when not merging</b>.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/Morph.java
 */
public class Morph
{
    protected AbstractMorph morph;

    public Morph()
    {}

    public Morph(AbstractMorph morph)
    {
        this.morph = morph;
    }

    public boolean isEmpty()
    {
        return this.morph == null;
    }

    public boolean set(AbstractMorph morph)
    {
        if (this.morph == null || !this.morph.canMerge(morph))
        {
            if (this.morph != null && morph != null)
            {
                morph.afterMerge(this.morph);
            }

            this.morph = morph;

            return true;
        }

        return false;
    }

    public void setDirect(AbstractMorph morph)
    {
        this.morph = morph;
    }

    public AbstractMorph get()
    {
        return this.morph;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Morph)
        {
            return Objects.equals(this.morph, ((Morph) obj).morph);
        }

        return super.equals(obj);
    }

    public AbstractMorph copy()
    {
        return MorphUtils.copy(this.morph);
    }

    public void copy(Morph morph)
    {
        this.set(morph.copy());
    }

    public void fromNBT(NbtCompound tag)
    {
        this.morph = MorphManager.INSTANCE.morphFromNBT(tag);
    }

    public NbtCompound toNBT()
    {
        return MorphUtils.toNBT(this.morph);
    }
}
