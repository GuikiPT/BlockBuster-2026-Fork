package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;

/**
 * Prevent fall damage ability (roadmap P49.1, registry id {@code prevent_fall}).
 *
 * <p>This ability is responsible for prevent the fall damage, and it is doing it
 * by modifying player's "fallDistance" field and setting it (or rather reseting)
 * to 0.0F.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/PreventFall.java
 */
public class PreventFall extends Ability
{
    @Override
    public void update(LivingEntity player)
    {
        player.fallDistance = 0.0F;
    }
}
