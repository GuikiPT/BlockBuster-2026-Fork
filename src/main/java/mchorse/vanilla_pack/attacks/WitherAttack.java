package mchorse.vanilla_pack.attacks;

import mchorse.metamorph.api.abilities.IAttackAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Wither attack ability (roadmap P49.1, registry id {@code wither}).
 *
 * <p>This ability simple adds on a target wither effect.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/attacks/WitherAttack.java
 */
public class WitherAttack implements IAttackAbility
{
    @Override
    public void attack(Entity target, LivingEntity source)
    {
        if (target instanceof LivingEntity)
        {
            ((LivingEntity) target).addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 200));
        }
    }
}
