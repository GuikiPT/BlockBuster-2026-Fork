package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * Abstract potion ability (roadmap P49.1).
 *
 * <p>This is class is responsible for adding specific given potion effect from
 * subclasses. You can also give the duration for the potion effect.</p>
 *
 * <p><b>Not a registered id</b> — it is the shared base of {@link FireProof}
 * and {@link NightVision}; legacy registers 15 ability ids out of 16 classes.</p>
 *
 * <p>API translation: 1.12 {@code Potion} → yarn {@link StatusEffect};
 * {@code PotionEffect(potion, duration, amplifier, ambient, showParticles)} →
 * {@link StatusEffectInstance} with the same five-argument shape;
 * {@code getActivePotionEffect}/{@code addPotionEffect}/{@code removePotionEffect}
 * → {@code getStatusEffect}/{@code addStatusEffect}/{@code removeStatusEffect}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/PotionAbility.java
 */
public abstract class PotionAbility extends Ability
{
    protected StatusEffect potion;
    protected int duration = 1200;

    @Override
    public void update(LivingEntity target)
    {
        StatusEffectInstance effect = target.getStatusEffect(this.potion);

        if (effect == null || effect.getDuration() < 300)
        {
            this.onDemorph(target);
            this.onMorph(target);
        }
    }

    @Override
    public void onMorph(LivingEntity target)
    {
        target.addStatusEffect(new StatusEffectInstance(this.potion, this.duration, 0, false, false));
    }

    @Override
    public void onDemorph(LivingEntity target)
    {
        target.removeStatusEffect(this.potion);
    }
}
