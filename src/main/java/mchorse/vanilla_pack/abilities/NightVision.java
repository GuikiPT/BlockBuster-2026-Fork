package mchorse.vanilla_pack.abilities;

import net.minecraft.entity.effect.StatusEffects;

/**
 * Night vision ability (roadmap P49.1, registry id {@code night_vision}).
 *
 * <p>Grants night vision effect for given morph. Mostly used by bat.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/NightVision.java
 */
public class NightVision extends PotionAbility
{
    public NightVision()
    {
        this.potion = StatusEffects.NIGHT_VISION;
    }
}
