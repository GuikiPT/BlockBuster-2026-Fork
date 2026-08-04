package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Potions action (roadmap P49.1, registry id {@code potions}).
 *
 * <p>This action is responsible for throwing a splash potion in the direction
 * where player looks.</p>
 *
 * <p>This action may throw instant harm, slowness, weakness or poison splash
 * potions depending on randomness, but most of the time it'll be instant harm
 * potion.</p>
 *
 * <p>Of course, some of the code of this action was taken fron
 * {@link WitchEntity}.</p>
 *
 * <p>API translation: {@code PotionTypes} → {@link net.minecraft.potion.Potions};
 * {@code PotionUtils.addPotionToItemStack} → {@link PotionUtil#setPotion};
 * {@code new EntityPotion(world, thrower, stack)} →
 * {@code new PotionEntity(world, thrower)} + {@code setItem(stack)};
 * {@code shoot(…)} → {@code setVelocity(…)}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Potions.java
 */
public class Potions implements IAction
{
    /**
     * The legacy weighted pick, extracted so the distribution is testable
     * headlessly with a seeded RNG (roadmap P49.1 verification).
     *
     * <p>Note that each branch draws its <b>own</b> float — this is not a
     * cumulative-weight roll — so the effective chances are 20% slowness,
     * 8% weakness (0.8 × 0.1), 3.6% poison (0.8 × 0.9 × 0.05) and the rest
     * harming. Legacy (and vanilla's witch) do exactly this.</p>
     */
    public static Potion pickPotion(Random random)
    {
        Potion effect = net.minecraft.potion.Potions.HARMING;

        if (random.nextFloat() < 0.2)
        {
            effect = net.minecraft.potion.Potions.SLOWNESS;
        }
        else if (random.nextFloat() < 0.1)
        {
            effect = net.minecraft.potion.Potions.WEAKNESS;
        }
        else if (random.nextFloat() < 0.05)
        {
            effect = net.minecraft.potion.Potions.POISON;
        }

        return effect;
    }

    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        World world = target.getWorld();

        if (world.isClient)
        {
            return;
        }

        if (target instanceof PlayerEntity && ((PlayerEntity) target).getAttackCooldownProgress(0.0F) < 1)
        {
            return;
        }

        Vec3d look = target.getRotationVec(1.0F);
        Potion effect = pickPotion(target.getRandom());

        ItemStack stack = PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION), effect);
        PotionEntity potion = new PotionEntity(world, target);

        potion.setItem(stack);

        /* LEGACY BUG (load-bearing): the pitch nudge is a no-op — setVelocity
         * (1.12: shoot) recomputes yaw/pitch from the velocity vector right
         * after, discarding it. Copied from the vanilla witch, kept verbatim. */
        potion.setPitch(potion.getPitch() + 20.0F);
        potion.setVelocity(look.x, look.y, look.z, 0.85F, 2.0F);

        world.spawnEntity(potion);

        if (target instanceof PlayerEntity)
        {
            ((PlayerEntity) target).resetLastAttackedTicks();
        }
    }
}
