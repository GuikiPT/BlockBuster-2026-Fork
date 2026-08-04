package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Small fireball action (roadmap P49.1, registry id {@code small_fireball}).
 *
 * <p>This action is responsible for shooting a fireball from player's face. Used
 * by blaze morph.</p>
 *
 * <p>API translation: same set as {@link Fireball} —
 * {@code EntitySmallFireball} → {@link SmallFireballEntity}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/SmallFireball.java
 */
public class SmallFireball implements IAction
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

        SmallFireballEntity fireball = new SmallFireballEntity(world, target, d2, d3, d4);

        fireball.setPosition(target.getX(), target.getY() + target.getHeight() * 0.9, target.getZ());

        world.spawnEntity(fireball);

        if (target instanceof PlayerEntity)
        {
            ((PlayerEntity) target).resetLastAttackedTicks();
        }
    }
}
