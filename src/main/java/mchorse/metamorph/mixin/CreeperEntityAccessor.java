package mchorse.metamorph.mixin;

import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link CreeperEntity}'s private explosion radius (roadmap P49.1).
 *
 * <p>Legacy {@code vanilla_pack.actions.Explode} read the field through Forge's
 * {@code ReflectionHelper.getPrivateValue(EntityCreeper.class, creeper,
 * "explosionRadius", "field_82226_g")}. The field is still private in 1.20.4
 * (and still NBT-settable via {@code ExplosionRadius}), so a single
 * {@code @Accessor} replaces the SRG reflection with no behavioural
 * difference.</p>
 */
@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor
{
    @Accessor("explosionRadius")
    int metamorph$getExplosionRadius();
}
