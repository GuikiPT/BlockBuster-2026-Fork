package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.EntityMorph;
import mchorse.metamorph.mixin.CreeperEntityAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Explode action (roadmap P49.1, registry id {@code explode}).
 *
 * <p>This action makes an explosion and also kills the player. Why kill also the
 * player? Because it won't be so creeper if he won't die.</p>
 *
 * <p>EXPLOSIONS!!! Mr. Torgue approves this action.</p>
 *
 * <p>API translation: the creeper's private {@code explosionRadius} moves from
 * Forge SRG reflection to the {@link CreeperEntityAccessor} mixin;
 * {@code getPowered()} → {@code shouldRenderOverlay()} (yarn's name for the
 * charged flag); {@code DamageSource.OUT_OF_WORLD} →
 * {@code getDamageSources().outOfWorld()}.</p>
 *
 * <p>Deviation (documented): 1.12's {@code createExplosion(…, isSmoking = true)}
 * destroyed terrain <b>unconditionally</b>, so this maps to
 * {@link World.ExplosionSourceType#TNT} rather than {@code MOB} — {@code MOB}
 * would newly make the action obey the {@code mobGriefing} game rule, which
 * 1.12.2 did not.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Explode.java
 */
public class Explode implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        if (target.getWorld().isClient)
        {
            return;
        }

        int explosionPower = 3;
        boolean isPowered = false;

        if (morph instanceof EntityMorph)
        {
            LivingEntity entity = ((EntityMorph) morph).getEntity();

            if (entity instanceof CreeperEntity)
            {
                explosionPower = ((CreeperEntityAccessor) entity).metamorph$getExplosionRadius();
                isPowered = ((CreeperEntity) entity).shouldRenderOverlay();
            }
        }

        float f = isPowered ? 2.0F : 1.0F;

        target.getWorld().createExplosion(target, target.getX(), target.getY(), target.getZ(), explosionPower * f, World.ExplosionSourceType.TNT);

        if (!(target instanceof PlayerEntity) || (target instanceof PlayerEntity && !((PlayerEntity) target).isCreative()))
        {
            target.damage(target.getDamageSources().outOfWorld(), target.getMaxHealth());
        }
    }
}
