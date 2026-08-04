package mchorse.metamorph.entity;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Replaces the hurt/death/step sounds a morphed player makes (roadmap P54.2).
 *
 * <p>Modern replacement for the legacy Forge {@code PlaySoundAtEntityEvent}
 * string-suffix matching ({@code .hurt}/{@code .death}/{@code .step}). Fabric
 * has no such event, so the {@code PlayerEntity.getHurtSound}/{@code
 * getDeathSound}/{@code playStepSound} calls are mixin-injected instead — a
 * <b>more precise</b> hook than matching vanilla path conventions, and the
 * hurt injection receives the real {@link DamageSource} directly (no need for
 * the recorded {@code lastDamageSource}).</p>
 *
 * <p>The decision logic is factored into pure {@code resolve*} helpers for the
 * P54.2 decision-table test. The {@code NO_SOUND} sentinel is preserved for API
 * parity; the mixins realize a suppression by returning {@code null} (vanilla:
 * null sound = silence), never by handing {@code NO_SOUND} back to vanilla.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/entity/SoundHandler.java
 */
public class SoundHandler
{
    /**
     * Sentinel a morph returns from {@code getHurtSound}/{@code getDeathSound}
     * to <b>cancel</b> the sound entirely (legacy {@code metamorph:no_sound}).
     * Kept unregistered: it is only ever compared by identity, never played.
     */
    public static final SoundEvent NO_SOUND = SoundEvent.of(new Identifier(Metamorph.MOD_ID, "no_sound"));

    /**
     * Default hurt-sound damage source when none was recorded (legacy {@code
     * DamageSource.GENERIC}). 1.20.4 damage sources are world/registry-bound,
     * so there is no constant an initializer could assign: it is captured
     * lazily off the hurt entity's own world by {@link #genericDamage} the
     * first time a caller actually needs the fallback, and cached here
     * afterwards (a {@code DamageSource} is immutable and the generic type is
     * a vanilla built-in present in every dynamic registry).
     *
     * <p>In the production call graph the fallback is never reached: the only
     * caller is {@code PlayerEntitySoundMixin}'s {@code getHurtSound}
     * injection, which always carries the real {@link DamageSource}. It stays
     * for API parity with legacy's {@code getHurtSound(target)} overload, which
     * called {@code getHurtSound(target, null)}.</p>
     */
    public static DamageSource GENERIC_DAMAGE;

    /**
     * Seam supplying a player's current morph (from the P52 morphing
     * component). Null → not morphed / component absent.
     */
    public static MorphSoundProvider provider;

    public interface MorphSoundProvider
    {
        AbstractMorph currentMorph(PlayerEntity player);
    }

    public enum Decision
    {
        /** Leave the vanilla sound unchanged. */
        NO_CHANGE,
        /** Suppress the sound entirely (morph returned NO_SOUND). */
        SUPPRESS,
        /** Replace with {@link Resolution#sound}. */
        REPLACE
    }

    public static final class Resolution
    {
        public static final Resolution NO_CHANGE = new Resolution(Decision.NO_CHANGE, null);
        public static final Resolution SUPPRESS = new Resolution(Decision.SUPPRESS, null);

        public final Decision decision;
        public final SoundEvent sound;

        private Resolution(Decision decision, SoundEvent sound)
        {
            this.decision = decision;
            this.sound = sound;
        }

        public static Resolution replace(SoundEvent sound)
        {
            return new Resolution(Decision.REPLACE, sound);
        }
    }

    public enum StepDecision
    {
        /** Play the vanilla step sound. */
        VANILLA,
        /** Cancel the step (anti-phantom-step: hitbox mismatch). */
        SUPPRESS,
        /** Cancel vanilla and play the morph's custom step sound. */
        CUSTOM
    }

    private static Resolution resolve(SoundEvent newSound)
    {
        if (newSound == NO_SOUND)
        {
            return Resolution.SUPPRESS;
        }

        if (newSound != null)
        {
            return Resolution.replace(newSound);
        }

        return Resolution.NO_CHANGE;
    }

    /**
     * Resolve the hurt sound. Uses the real damage source when present, else
     * the {@link #GENERIC_DAMAGE} fallback (legacy passed the recorded source,
     * which could be null).
     */
    public static Resolution resolveHurt(AbstractMorph morph, LivingEntity player, DamageSource source)
    {
        if (morph == null)
        {
            return Resolution.NO_CHANGE;
        }

        DamageSource src = source != null ? source : genericDamage(player);

        return resolve(morph.getHurtSound(player, src));
    }

    /**
     * The {@link #GENERIC_DAMAGE} fallback, captured off {@code player}'s world
     * on first use (yarn {@code Entity.getDamageSources().generic()} — the
     * 1.20.4 spelling of legacy's {@code DamageSource.GENERIC} constant).
     *
     * <p>Total: no player, or a player not attached to a world yet, leaves the
     * field {@code null} and the morph receives {@code null}, which is exactly
     * what it received before this fallback existed.</p>
     */
    public static DamageSource genericDamage(LivingEntity player)
    {
        if (GENERIC_DAMAGE == null && player != null)
        {
            try
            {
                GENERIC_DAMAGE = player.getDamageSources().generic();
            }
            catch (Exception e)
            {
                /* No world / no dynamic registry yet — stay null, never crash a
                 * sound lookup over it. */
            }
        }

        return GENERIC_DAMAGE;
    }

    /** Resolve the death sound. */
    public static Resolution resolveDeath(AbstractMorph morph, LivingEntity player)
    {
        if (morph == null)
        {
            return Resolution.NO_CHANGE;
        }

        return resolve(morph.getDeathSound(player));
    }

    /**
     * Resolve the step behavior. Anti-phantom-step: when the player hitbox does
     * not match the morph hitbox the step is cancelled even if the morph has no
     * custom step sound (each tick the player entity reverts to the default
     * hitbox; a smaller morph hitbox can trigger movement, which triggers a
     * spurious step sound). Otherwise a custom step sound replaces vanilla.
     */
    public static StepDecision resolveStep(AbstractMorph morph, LivingEntity player, boolean hitboxMismatch)
    {
        if (morph == null)
        {
            return StepDecision.VANILLA;
        }

        if (hitboxMismatch)
        {
            return StepDecision.SUPPRESS;
        }

        if (morph.hasCustomStepSound(player))
        {
            return StepDecision.CUSTOM;
        }

        return StepDecision.VANILLA;
    }

    /** Whether the player's hitbox differs from the morph's (anti-phantom-step). */
    public static boolean hitboxMismatch(AbstractMorph morph, LivingEntity player)
    {
        return player.getWidth() != morph.getWidth(player) || player.getHeight() != morph.getHeight(player);
    }

    /** The player's current morph via the seam, or null. */
    public static AbstractMorph currentMorph(PlayerEntity player)
    {
        return provider == null ? null : provider.currentMorph(player);
    }
}
