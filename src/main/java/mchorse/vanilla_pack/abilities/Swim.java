package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Swim ability (roadmap P49.1, registry id {@code swim}).
 *
 * <p>This ability makes player a better swimmer. What it does, it basically
 * increases the swim speed and also gives player more control over vertical
 * movement.</p>
 *
 * <p>API translation: {@code rotationYaw}/{@code rotationPitch} →
 * {@code getYaw()}/{@code getPitch()}; {@code motionX/Y/Z} →
 * {@code getVelocity()}/{@code setVelocity()} + {@code velocityModified};
 * {@code moveStrafing}/{@code moveForward} → {@code sidewaysSpeed}/
 * {@code forwardSpeed}; {@code isInWater()} → {@code isTouchingWater()}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Swim.java
 */
public class Swim extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        updateMotion(target);
    }

    private void updateMotion(LivingEntity target)
    {
        double speed = 0.35;

        if (target.isTouchingWater())
        {
            if (target.forwardSpeed != 0 || target.sidewaysSpeed != 0)
            {
                float a = target.getYaw() / 180 * (float) Math.PI;

                float f1 = MathHelper.sin(a);
                float f2 = MathHelper.cos(a);

                double x = (double) (target.sidewaysSpeed * f2 - target.forwardSpeed * f1) * speed;
                double z = (double) (target.forwardSpeed * f2 + target.sidewaysSpeed * f1) * speed;
                double y = -MathHelper.sin((float) (target.getPitch() / 180 * Math.PI)) * target.forwardSpeed * speed;

                target.setVelocity(x, y, z);
                target.velocityModified = true;
            }
            else
            {
                Vec3d motion = target.getVelocity();
                double y = motion.y * 0.6;

                if (y < 0)
                {
                    y = 0;
                }

                /* LEGACY BUG (load-bearing): the drift branch damps motionX and
                 * motionY but never motionZ, so a swimmer coasting due north or
                 * south keeps their speed while one coasting east/west loses it.
                 * Ported verbatim. */
                target.setVelocity(motion.x * 0.6, y, motion.z);
                target.velocityModified = true;
            }
        }
    }
}
