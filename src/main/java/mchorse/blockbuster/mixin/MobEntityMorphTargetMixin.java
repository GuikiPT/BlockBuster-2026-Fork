package mchorse.blockbuster.mixin;

import mchorse.metamorph.api.MorphHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * P52.1 — hostile-morph disguise targeting. Legacy Metamorph handled this on
 * Forge's {@code LivingSetAttackTargetEvent}; 1.20.4 has no equivalent event,
 * so this mixin intercepts {@code MobEntity.setTarget(LivingEntity)} and routes
 * the incoming target through {@link MorphHandler#filterAttackTarget}, which
 * nulls it when the target is a player disguised as a hostile morph that hasn't
 * provoked this mob.
 *
 * <p>Signature verified with javap against the loom named jar:
 * {@code MobEntity.setTarget(net.minecraft.entity.LivingEntity)}.</p>
 *
 * <p>SEAM(P53): the EntityMorph-specific branches (inner-entity exclusion +
 * retarget-onto-player) are added when {@code EntityMorph} lands — see
 * {@code MorphHandler.filterAttackTarget}.</p>
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMorphTargetMixin
{
    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private LivingEntity metamorph$filterMorphTarget(LivingEntity target)
    {
        return MorphHandler.filterAttackTarget((MobEntity) (Object) this, target);
    }
}
