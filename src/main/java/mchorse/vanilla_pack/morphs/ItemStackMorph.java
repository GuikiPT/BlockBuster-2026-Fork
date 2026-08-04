package mchorse.vanilla_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Shared parent of {@link BlockMorph} and {@link ItemMorph} (roadmap P53.2).
 *
 * <p>Contributes the {@code Lighting} flag — fullbright render when {@code false}
 * — which is written to NBT <b>only when false</b> (inverted-omission quirk).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/ItemStackMorph.java
 */
public abstract class ItemStackMorph extends AbstractMorph
{
    public boolean lighting = true;

    public abstract void setStack(ItemStack stack);

    public abstract ItemStack getStack();

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof ItemStackMorph)
        {
            ItemStackMorph morph = (ItemStackMorph) obj;

            result = result && this.lighting == morph.lighting;
        }

        return result;
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ItemStackMorph)
        {
            ItemStackMorph morph = (ItemStackMorph) from;

            this.lighting = morph.lighting;
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (!this.lighting)
        {
            tag.putBoolean("Lighting", this.lighting);
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Lighting"))
        {
            this.lighting = tag.getBoolean("Lighting");
        }
    }
}
