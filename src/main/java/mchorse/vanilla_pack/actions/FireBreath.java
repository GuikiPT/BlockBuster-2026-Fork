package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Ender dragon's fire breath (roadmap P49.1, registry id {@code fire_breath}).
 *
 * <p>Unlike the two fireball actions this one offsets the spawn position
 * <b>forwards</b> along the look vector ({@code d2 / d1} = the unit look) as
 * well as up — legacy did, keep it.</p>
 *
 * <p>API translation: {@code EntityDragonFireball} →
 * {@link DragonFireballEntity}; world event 1017 stays the dragon-fireball
 * sound event id; see {@link Fireball} for the rest.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/FireBreath.java
 */
public class FireBreath implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        if (target.getWorld().isClient)
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

        target.getWorld().syncWorldEvent((PlayerEntity) null, 1017, target.getBlockPos(), 0);

        DragonFireballEntity fireball = new DragonFireballEntity(target.getWorld(), target, d2, d3, d4);

        fireball.setPosition(target.getX() + d2 / d1, target.getY() + target.getHeight() * 0.9, target.getZ() + d4 / d1);

        target.getWorld().spawnEntity(fireball);

        if (target instanceof PlayerEntity)
        {
            ((PlayerEntity) target).resetLastAttackedTicks();
        }
    }
}
