package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Rotten ability (roadmap P49.1, registry id {@code rotten}).
 *
 * <p>Prevents a character from being posion. Mostly used for undead.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Rotten.java
 */
public class Rotten extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        if (target.hasStatusEffect(StatusEffects.POISON))
        {
            this.onMorph(target);
        }
    }

    @Override
    public void onMorph(LivingEntity target)
    {
        target.removeStatusEffect(StatusEffects.POISON);
    }
}
