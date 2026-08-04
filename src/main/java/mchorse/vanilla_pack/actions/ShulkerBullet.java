package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.EntityUtils;
import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Shulker bullet action (roadmap P49.1, registry id {@code shulker_bullet}).
 *
 * <p>Fires a homing shulker bullet at whatever the morphed entity is looking at
 * within 32 blocks (see {@link EntityUtils#getTargetEntity}). The attack
 * cooldown is reset whether or not a target was found — legacy's.</p>
 *
 * <p>API translation: {@code EntityShulkerBullet} → {@link ShulkerBulletEntity};
 * {@code EnumFacing.Axis.Z} → {@link Direction.Axis#Z}; {@code posX/posZ}
 * assignment → {@code setPosition} (Y is left at whatever the constructor put
 * it, exactly like legacy, which only overwrote X and Z).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/ShulkerBullet.java
 */
public class ShulkerBullet implements IAction
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

        Entity toShoot = EntityUtils.getTargetEntity(target, 32);

        if (toShoot != null)
        {
            target.playSound(SoundEvents.ENTITY_SHULKER_SHOOT, 2.0F, (target.getRandom().nextFloat() - target.getRandom().nextFloat()) * 0.2F + 1.0F);

            ShulkerBulletEntity fireball = new ShulkerBulletEntity(world, target, toShoot, Direction.Axis.Z);

            fireball.setPosition(target.getX(), fireball.getY(), target.getZ());

            world.spawnEntity(fireball);
        }

        if (target instanceof PlayerEntity)
        {
            ((PlayerEntity) target).resetLastAttackedTicks();
        }
    }
}
