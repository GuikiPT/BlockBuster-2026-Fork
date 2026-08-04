package mchorse.vanilla_pack.actions;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Llama spit action (roadmap P49.1, registry id {@code spit}).
 *
 * <p>API translation: {@code new EntityLlamaSpit(world)} →
 * {@code new LlamaSpitEntity(EntityType.LLAMA_SPIT, world)} (the world-only
 * constructor is gone); the public {@code spit.owner} field →
 * {@link net.minecraft.entity.projectile.ProjectileEntity#setOwner};
 * {@code shoot(…)} → {@code setVelocity(…)}.</p>
 *
 * <p>Quirks preserved: the {@code d1 = 4.0} scale is declared but never applied
 * (the spit is shot with the raw unit look vector), and the spawn Y uses
 * {@code height * 0.8} rather than the fireballs' {@code 0.9}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Spit.java
 */
public class Spit implements IAction
{
    @Override
    public void execute(LivingEntity target, AbstractMorph morph)
    {
        if (target.getWorld().isClient)
        {
            return;
        }

        LlamaSpitEntity spit = new LlamaSpitEntity(EntityType.LLAMA_SPIT, target.getWorld());

        Vec3d vec3d = target.getRotationVec(1.0F);

        double d1 = 4.0D;
        double d2 = vec3d.x;
        double d3 = vec3d.y;
        double d4 = vec3d.z;

        spit.setPosition(target.getX() + d2 * 1.5, target.getY() + target.getHeight() * 0.8F, target.getZ() + d4 * 1.5);
        spit.setVelocity(d2, d3, d4, (float) 1.0F, 1.0F);

        if (morph instanceof EntityMorph)
        {
            LivingEntity entity = ((EntityMorph) morph).getEntity();

            if (entity instanceof LlamaEntity)
            {
                spit.setOwner(entity);
            }
        }

        target.getWorld().playSound((PlayerEntity) null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_LLAMA_SPIT, target.getSoundCategory(), 1.0F, 1.0F + (target.getRandom().nextFloat() - target.getRandom().nextFloat()) * 0.2F);
        target.getWorld().spawnEntity(spit);
    }
}
