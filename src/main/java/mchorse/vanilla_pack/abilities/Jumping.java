package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Jumping ability (roadmap P49.1, registry id {@code jumping}).
 *
 * <p>Makes player jump whenever he moves and on the ground. Just like a
 * slime!</p>
 *
 * <p>API translation: {@code moveStrafing}/{@code moveForward} → yarn
 * {@link LivingEntity#sidewaysSpeed}/{@link LivingEntity#forwardSpeed} (same
 * public fields, renamed); {@code isInWater()} →
 * {@link LivingEntity#isTouchingWater()}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Jumping.java
 */
public class Jumping extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        boolean moving = target.sidewaysSpeed != 0 || target.forwardSpeed != 0;

        if (target.isOnGround() && moving && !target.isTouchingWater())
        {
            Vec3d motion = target.getVelocity();

            target.setVelocity(motion.x, motion.y + 0.5D, motion.z);
            target.velocityModified = true;
        }
    }
}
