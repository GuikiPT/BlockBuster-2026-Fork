package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Fireball action (roadmap P49.1, registry id {@code fireball}).
 *
 * <p>This action is responsible for shooting a fireball from player's face. Used
 * by ghast morph.</p>
 *
 * <p>API translation: {@code EntityLargeFireball} → {@link FireballEntity}, and
 * its {@code explosionPower} is a constructor argument in 1.20.4 rather than a
 * writable field ({@code 1}, as legacy assigned); {@code getLook(1F)} →
 * {@code getRotationVec(1F)}; {@code getCooledAttackStrength(0F)} →
 * {@code getAttackCooldownProgress(0F)}; {@code resetCooldown()} →
 * {@code resetLastAttackedTicks()}; {@code world.playEvent} →
 * {@code world.syncWorldEvent}; {@code new BlockPos(entity)} →
 * {@code entity.getBlockPos()}; {@code posX/Y/Z} assignment →
 * {@code setPosition}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Fireball.java
 */
public class Fireball implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        World world = target.getWorld();

        if (world.isClient)
        {
            return;
        }

        if (target instanceof PlayerEntity && ((PlayerEntity) target).getAttackCooldownProgress(0.0F) < 1)
        {
            return;
        }

        Vec3d vec3d = target.getRotationVec(1.0F);

        double d1 = 4.0D;
        double d2 = vec3d.x * d1;
        double d3 = vec3d.y * d1;
        double d4 = vec3d.z * d1;

        world.syncWorldEvent((PlayerEntity) null, 1016, target.getBlockPos(), 0);

        FireballEntity fireball = new FireballEntity(world, target, d2, d3, d4, 1);

        fireball.setPosition(target.getX(), target.getY() + target.getHeight() * 0.9, target.getZ());

        world.spawnEntity(fireball);

        if (target instanceof PlayerEntity)
        {
            ((PlayerEntity) target).resetLastAttackedTicks();
        }
    }
}
