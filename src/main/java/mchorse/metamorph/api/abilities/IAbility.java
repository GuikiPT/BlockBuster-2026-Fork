package mchorse.metamorph.api.abilities;

import net.minecraft.entity.LivingEntity;

/**
 * Ability interface
 *
 * This interface should provide a method which is responsible for updating
 * entity every tick, with ability to setup some behavior on player before
 * morphing, and reset player, in given way, on player's demorph.
 *
 * <p>Bundled Metamorph 1.4 port (roadmap P47). Concrete vanilla-pack
 * abilities land in P49.1; this interface is the contract MorphSettings
 * dispatches through.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/abilities/IAbility.java
 */
public interface IAbility
{
    /**
     * This method is responsible for updating a player based on ability's
     * function.
     */
    public void update(LivingEntity target);

    /**
     * This method should be invoked when the player is about to get morphed.
     */
    public void onMorph(LivingEntity target);

    /**
     * This method should be invoked when the player morphs into other morph
     * without this ability.
     */
    public void onDemorph(LivingEntity target);
}
