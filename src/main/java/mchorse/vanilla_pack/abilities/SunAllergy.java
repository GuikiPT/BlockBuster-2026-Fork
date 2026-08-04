package mchorse.vanilla_pack.abilities;

import java.util.Random;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Sun allergy ability (roadmap P49.1, registry id {@code sun_allergy}).
 *
 * <p>This abilitiy does cool stuff. It sets player on fire when he's on the sun.
 * It will be used by the coolest mobs in the game skeleton and zombie.</p>
 *
 * <p>This is more like a disability than an ability *Ba-dum-pam-dum-tsss*</p>
 *
 * <p>API translation: {@code world.isDaytime()} → {@code world.isDay()};
 * {@code world.isRemote} → {@code world.isClient}; {@code entity.getBrightness()}
 * → {@link net.minecraft.entity.Entity#getBrightnessAtEyes()};
 * {@code world.canSeeSky(pos)} → {@code world.isSkyVisible(pos)};
 * {@code getItemStackFromSlot}/{@code setItemStackToSlot} →
 * {@code getEquippedStack}/{@code equipStack};
 * {@code isItemStackDamageable}/{@code setItemDamage}/{@code getItemDamage} →
 * {@code isDamageable}/{@code setDamage}/{@code getDamage};
 * {@code renderBrokenItemStack(stack)} →
 * {@link LivingEntity#sendEquipmentBreakStatus(EquipmentSlot)} (1.20.4 derives
 * the break particles from the slot rather than the stack);
 * {@code setFire(8)} → {@code setOnFireFor(8)}.</p>
 *
 * <p>The ability keeps its own {@link Random} (not the entity's) — legacy did,
 * and the instance is a shared singleton across every morph using it.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/SunAllergy.java
 */
public class SunAllergy extends Ability
{
    private BlockPos.Mutable pos = new BlockPos.Mutable(0, 0, 0);
    private Random random = new Random();

    @Override
    public void update(LivingEntity target)
    {
        if (!target.getWorld().isDay() || target.getWorld().isClient)
        {
            return;
        }

        float brightness = target.getBrightnessAtEyes();
        boolean random = this.random.nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F;
        this.pos.set(target.getX(), target.getY() + target.getStandingEyeHeight(), target.getZ());

        /* Taken from EntityZombie class and slightly modified */
        if (brightness > 0.5 && random && target.getWorld().isSkyVisible(this.pos))
        {
            boolean flag = true;
            ItemStack itemstack = target.getEquippedStack(EquipmentSlot.HEAD);

            /* If target has a head slot on the head, then damage it */
            if (!itemstack.isEmpty())
            {
                boolean isCreativePlayer = target instanceof PlayerEntity && ((PlayerEntity) target).isCreative();

                /* Unless it's damagable or creative player wears it */
                if (itemstack.isDamageable() && !isCreativePlayer)
                {
                    itemstack.setDamage(itemstack.getDamage() + this.random.nextInt(2));

                    if (itemstack.getDamage() >= itemstack.getMaxDamage())
                    {
                        target.sendEquipmentBreakStatus(EquipmentSlot.HEAD);
                        target.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    }
                }

                flag = false;
            }

            if (flag)
            {
                target.setOnFireFor(8);
            }
        }
    }
}
