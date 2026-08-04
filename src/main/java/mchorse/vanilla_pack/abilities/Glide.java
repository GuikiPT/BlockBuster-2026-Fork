package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Glide ability (roadmap P49.1, registry id {@code glide}).
 *
 * <p>This ability makes player fall much slower. Sneak to disable gliding
 * effect.</p>
 *
 * <p>API translation: {@code onGround} → {@link LivingEntity#isOnGround()};
 * {@code isElytraFlying()} → {@link LivingEntity#isFallFlying()};
 * {@code capabilities.isFlying} → {@code getAbilities().flying}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Glide.java
 */
public class Glide extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        boolean isFlying = target instanceof PlayerEntity && ((PlayerEntity) target).getAbilities().flying;
        Vec3d motion = target.getVelocity();

        if (!target.isOnGround() && motion.y < 0.0D && !isFlying && !target.isFallFlying() && !target.isSneaking())
        {
            target.setVelocity(motion.x, motion.y * 0.6D, motion.z);
            target.velocityModified = true;
            target.fallDistance = 0.0F;
        }
    }
}
