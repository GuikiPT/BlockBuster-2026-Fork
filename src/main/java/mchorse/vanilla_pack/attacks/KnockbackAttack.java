package mchorse.vanilla_pack.attacks;

import mchorse.metamorph.api.MorphHandler;
import mchorse.metamorph.api.abilities.IAttackAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Knock back attack (roadmap P49.1, registry id {@code knockback}).
 *
 * <p>This makes the entity go up in the air. Really cool!</p>
 *
 * <p>The motion is applied through {@link MorphHandler}'s future-task queue, not
 * synchronously: the queue drains one task per tick, and that one-tick delay is
 * what makes the knockback survive the attack's own velocity handling. Do not
 * "fix" it into a direct call.</p>
 *
 * <p>API translation: {@code motionX/Y/Z} → {@code setVelocity} +
 * {@code velocityModified} (the flag is what makes the server resend the new
 * velocity to the victim's client); {@code world.isRemote} →
 * {@code world.isClient}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/attacks/KnockbackAttack.java
 */
public class KnockbackAttack implements IAttackAbility
{
    @Override
    public void attack(final Entity target, LivingEntity source)
    {
        final Vec3d look = source.getRotationVec(1.0F);
        final double d = 1;

        Runnable task = new Runnable()
        {
            @Override
            public void run()
            {
                target.setVelocity(look.x * d, 1, look.z * d);
                target.velocityModified = true;
            }
        };

        if (!source.getWorld().isClient)
        {
            MorphHandler.FUTURE_TASKS_SERVER.add(task);
        }
        else
        {
            MorphHandler.FUTURE_TASKS_CLIENT.add(task);
        }
    }
}
