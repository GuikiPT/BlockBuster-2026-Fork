package mchorse.vanilla_pack.abilities;

import net.minecraft.entity.effect.StatusEffects;

/**
 * Fire proof ability (roadmap P49.1, registry id {@code fire_proof}).
 *
 * <p>This abilitiy grants you fire immunity. So basically you're fire proof.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/FireProof.java
 */
public class FireProof extends PotionAbility
{
    public FireProof()
    {
        this.potion = StatusEffects.FIRE_RESISTANCE;
    }
}
