package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;

/**
 * Climbing ability (roadmap P49.1, registry id {@code climb}).
 *
 * <p>This ability makes player climb on the wall. Don't add this ability to the
 * any other mobs except Spiders, otherwise player will turn into Spider man.</p>
 *
 * <p>API translation: 1.12 {@code collidedHorizontally} → 1.20.4
 * {@link net.minecraft.entity.Entity#horizontalCollision}; direct
 * {@code motionY} writes → {@code setVelocity} + {@code velocityModified}
 * (1.20.4 keeps velocity in an immutable {@code Vec3d}, and the flag is what
 * makes the server resend it).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Climb.java
 */
public class Climb extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        /* Works properly only on client side :( */
        if (target.horizontalCollision)
        {
            target.setVelocity(target.getVelocity().x, target.isSneaking() ? 0 : 0.2D, target.getVelocity().z);
            target.velocityModified = true;
        }

        /* Fucking server doesn't handles climbing properly, that's why I
         * have to reset fall distance in order to save player lifes */
        target.fallDistance = 0.0F;
    }
}
