package mchorse.vanilla_pack.attacks;

import mchorse.metamorph.api.abilities.IAttackAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Poison attack (roadmap P49.1, registry id {@code poison}).
 *
 * <p>This attack poisons the target. Used by cave spider morph in the main
 * mod.</p>
 *
 * <p>API translation: {@code new PotionEffect(MobEffects.POISON, 200)} →
 * {@code new StatusEffectInstance(StatusEffects.POISON, 200)}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/attacks/PoisonAttack.java
 */
public class PoisonAttack implements IAttackAbility
{
    @Override
    public void attack(Entity target, LivingEntity source)
    {
        if (target instanceof LivingEntity)
        {
            ((LivingEntity) target).addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200));
        }
    }
}
