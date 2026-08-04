package mchorse.vanilla_pack.actions;

import org.jetbrains.annotations.Nullable;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;

/**
 * Endermite action (roadmap P49.1, registry id {@code endermite}).
 *
 * <p>Teleports player to a random location nearby (±32 blocks horizontally,
 * ±32 vertically).</p>
 *
 * <p>API translation: {@code getRNG()} → {@link LivingEntity#getRandom()}
 * (yarn's {@code net.minecraft.util.math.random.Random});
 * {@code attemptTeleport(x, y, z)} →
 * {@link LivingEntity#teleport(double, double, double, boolean)} with
 * {@code particleEffects = true} — 1.12's {@code attemptTeleport} always
 * emitted the portal particles and the teleport sound, and the boolean is how
 * 1.20.4 asks for them; {@code prevPosX/Y/Z} → {@code prevX/Y/Z};
 * {@code SoundEvents.ENTITY_ENDERMEN_TELEPORT} →
 * {@code ENTITY_ENDERMAN_TELEPORT} (yarn spells the mob singular).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/actions/Endermite.java
 */
public class Endermite implements IAction
{
    @Override
    public void execute(LivingEntity target, @Nullable AbstractMorph morph)
    {
        Random rand = target.getRandom();

        /* Teleports within 32 block radius */
        double x = target.getX() + (rand.nextDouble() - 0.5D) * 64.0D;
        double y = target.getY() + (double) (rand.nextInt(64) - 32);
        double z = target.getZ() + (rand.nextDouble() - 0.5D) * 64.0D;

        if (target.teleport(x, y, z, true))
        {
            target.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            target.getWorld().playSound((PlayerEntity) null, target.prevX, target.prevY, target.prevZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, target.getSoundCategory(), 1.0F, 1.0F);
        }
    }
}
