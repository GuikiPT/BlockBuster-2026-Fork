package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;

/**
 * Water allergy ability (roadmap P49.1, registry id {@code water_allergy}).
 *
 * <p>This ability is responsible for damaging the player when he is in the water
 * primarily will be used in Enderman's morph.</p>
 *
 * <p>This is more like a disability than an ability *Ba-dum-pam-dum-tsss*</p>
 *
 * <p>API translation: 1.20.4 damage types are registry entries, so the static
 * {@code DamageSource.DROWN} becomes
 * {@code entity.getDamageSources().drown()} (same {@code minecraft:drown} type).
 * The <b>wet</b> check ({@code isWet()}, which rain and bubble columns satisfy
 * too, not just submersion) is legacy's and is kept.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/WaterAllergy.java
 */
public class WaterAllergy extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        if (target.isWet())
        {
            target.damage(target.getDamageSources().drown(), 1.0F);
        }
    }
}
