package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Snowball action (roadmap P49.1, registry id {@code snowball}).
 *
 * <p>Throws a snowball in the look direction of the player. The code is taken
 * from {@link Fireball#execute} and {@code EntitySnowman
 * #attackEntityWithRangedAttack} (1.20.4: {@code SnowGolemEntity#shootAt}).</p>
 *
 * <p>Unlike the fireball actions this one has <b>no</b> attack-cooldown gate and
 * never resets the cooldown — legacy's, so it can be spammed.</p>
 *
 * <p>API translation: {@code EntitySnowball} → {@link SnowballEntity};
 * {@code SoundEvents.ENTITY_SNOWMAN_SHOOT} → {@code ENTITY_SNOW_GOLEM_SHOOT};
 * direct {@code motionX/Y/Z} writes → {@code setVelocity}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Snowball.java
 */
public class Snowball implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        if (!target.getWorld().isClient)
        {
            SnowballEntity snowball = new SnowballEntity(target.getWorld(), target);
            Vec3d vec3d = target.getRotationVec(1.0F);

            double d1 = 4.0D;
            double d2 = vec3d.x * d1;
            double d3 = vec3d.y * d1;
            double d4 = vec3d.z * d1;

            snowball.setPosition(target.getX(), target.getY() + target.getHeight() * 0.9F, target.getZ());
            snowball.setVelocity(d2, d3, d4);

            target.playSound(SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, 1.0F, 1.0F / (target.getRandom().nextFloat() * 0.4F + 0.8F));
            target.getWorld().spawnEntity(snowball);
        }
    }
}
