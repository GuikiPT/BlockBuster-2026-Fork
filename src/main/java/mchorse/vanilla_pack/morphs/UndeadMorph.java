package mchorse.vanilla_pack.morphs;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Undead morph (roadmap P53.1).
 *
 * <p>Prevents the sun-allergy hurt/idle grunts of zombie/skeleton disguises by
 * temporarily equipping an Unbreakable leather helmet on the inner entity while
 * ticking it (only when the player isn't burning), then removing it in the same
 * tick — the helmet is never persisted and never rendered.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/UndeadMorph.java
 */
public class UndeadMorph extends EntityMorph
{
    protected static final ItemStack HELMET;

    static
    {
        HELMET = new ItemStack(Items.LEATHER_HELMET);
        HELMET.getOrCreateNbt().putBoolean("Unbreakable", true);
    }

    @Override
    protected void updateEntity(LivingEntity target)
    {
        boolean preventNoise = !target.isOnFire();

        if (preventNoise)
        {
            this.entity.equipStack(EquipmentSlot.HEAD, HELMET);
        }

        super.updateEntity(target);

        if (preventNoise)
        {
            this.entity.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new UndeadMorph();
    }
}
