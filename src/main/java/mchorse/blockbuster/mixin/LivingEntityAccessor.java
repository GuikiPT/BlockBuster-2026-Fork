package mchorse.blockbuster.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for {@link LivingEntity}'s protected sound getters (roadmap P53).
 *
 * <p>Legacy {@code EntityMorph} reached the inner entity's hurt/death sounds
 * through private-method reflection with SRG names
 * ({@code func_184601_bQ}/{@code func_184615_bR}). On yarn 1.20.4 those are
 * {@code protected} methods on {@link LivingEntity}, so a single
 * {@code @Invoker} accessor replaces the entire reflection dance — no
 * behavioral difference, the morph just forwards the inner entity's own
 * sound.</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor
{
    @Invoker("getHurtSound")
    SoundEvent metamorph$invokeGetHurtSound(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent metamorph$invokeGetDeathSound();
}
