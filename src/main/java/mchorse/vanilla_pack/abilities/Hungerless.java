package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Hungerless ability (roadmap P49.1, registry id {@code hungerless}).
 *
 * <p>This ability is responsible for removing hunger potion effect from given
 * morph. Really good for zombies, since they're the only one who're going to
 * use them.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Hungerless.java
 */
public class Hungerless extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        if (target.hasStatusEffect(StatusEffects.HUNGER))
        {
            this.onMorph(target);
        }
    }

    @Override
    public void onMorph(LivingEntity target)
    {
        target.removeStatusEffect(StatusEffects.HUNGER);
    }
}
