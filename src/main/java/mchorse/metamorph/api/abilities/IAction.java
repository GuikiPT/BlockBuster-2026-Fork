package mchorse.metamorph.api.abilities;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;

/**
 * Action interface
 *
 * Just like an ability, but cooler. This interface, instead of changing player's
 * properties, it's actually does some kind of trick.
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/abilities/IAction.java
 */
public interface IAction
{
    /**
     * Execute an action. Depends on action's description, it can teleport
     * player, emit explosion, or something else.
     */
    public void execute(LivingEntity target, @Nullable AbstractMorph morph);
}
