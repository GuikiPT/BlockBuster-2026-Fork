package mchorse.metamorph.api.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Ability attack interface
 *
 * This interface is responsible for adding some additional effect on the
 * target (used primarly for wither, and other stuff)
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/abilities/IAttackAbility.java
 */
public interface IAttackAbility
{
    /**
     * Do something with attacked entity
     */
    public void attack(Entity target, LivingEntity source);
}
