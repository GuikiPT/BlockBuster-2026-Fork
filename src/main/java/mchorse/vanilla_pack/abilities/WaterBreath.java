package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Water breath ability (roadmap P49.1, registry id {@code water_breath}).
 *
 * <p>This ability grants its owner ability to stay in water and refill its
 * air — the inverted drowning of the squid morph. In water the morphing
 * component's {@code squidAir} (and the player's own air) is pinned at 300; on
 * land it counts down one per tick and, once it reaches −20, deals 2.0 drown
 * damage and resets to 0.</p>
 *
 * <p>API translation: {@code attackEntityFrom(DamageSource.DROWN, 2F)} →
 * {@code damage(getDamageSources().drown(), 2F)} (1.20.4 damage types are
 * registry entries); {@code isInWater()} → {@code isTouchingWater()};
 * {@code Morphing.get(player)} is the port's morphing component rather than a
 * Forge capability, but keeps the legacy name and accessors.</p>
 *
 * <p>Deviation (documented): legacy's {@code onMorph}/{@code onDemorph} cast
 * {@code target} to a player <b>unconditionally</b> and would have thrown a
 * {@code ClassCastException} for a non-player morph target. Both are guarded
 * with {@code instanceof} here — identical for every reachable input (only
 * players carry a morphing component) and it upholds the port's
 * never-crash rule.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/WaterBreath.java
 */
public class WaterBreath extends Ability
{
    /**
     * Legacy squid-air ceiling: the value both the component and the player's
     * own air bar get pinned to while submerged.
     */
    public static final int MAX_SQUID_AIR = 300;

    /**
     * Legacy drown threshold: the countdown runs 20 ticks <b>past</b> zero
     * before the drown tick fires.
     */
    public static final int DROWN_AIR = -20;

    /**
     * Pure form of the on-land countdown step: the value {@code squidAir}
     * becomes after one land tick. Extracted so the state machine is testable
     * headlessly (roadmap P49.1 verification).
     */
    public static int nextSquidAirOnLand(int squidAir)
    {
        int air = squidAir - 1;

        return air <= DROWN_AIR ? 0 : air;
    }

    /**
     * Pure form of the drown predicate: whether the land tick starting from
     * {@code squidAir} deals its 2.0 drown damage.
     */
    public static boolean drownsOnLand(int squidAir)
    {
        return squidAir - 1 <= DROWN_AIR;
    }

    @Override
    public void update(LivingEntity target)
    {
        updateAir(target);
    }

    private void updateAir(LivingEntity target)
    {
        if (target instanceof PlayerEntity)
        {
            IMorphing morphing = Morphing.get((PlayerEntity) target);

            if (morphing != null)
            {
                if (target.isTouchingWater())
                {
                    morphing.setSquidAir(MAX_SQUID_AIR);
                    target.setAir(MAX_SQUID_AIR);
                }
                else
                {
                    int squidAir = morphing.getSquidAir();

                    if (drownsOnLand(squidAir))
                    {
                        target.damage(target.getDamageSources().drown(), 2.0F);
                    }

                    morphing.setSquidAir(nextSquidAirOnLand(squidAir));
                }
            }
        }
    }

    /**
     * On morph, show squid air
     */
    @Override
    public void onMorph(LivingEntity target)
    {
        if (!(target instanceof PlayerEntity))
        {
            return;
        }

        IMorphing morphing = Morphing.get((PlayerEntity) target);

        if (morphing != null)
        {
            morphing.setSquidAir(target.getAir());
            morphing.setHasSquidAir(true);
        }
    }

    /**
     * On demorph, show regular player air again
     */
    @Override
    public void onDemorph(LivingEntity target)
    {
        if (!(target instanceof PlayerEntity))
        {
            return;
        }

        IMorphing morphing = Morphing.get((PlayerEntity) target);

        if (morphing != null)
        {
            target.setAir(morphing.getSquidAir());
            morphing.setHasSquidAir(false);
        }
    }
}
