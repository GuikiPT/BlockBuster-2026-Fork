package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Fly ability (roadmap P49.1, registry id {@code fly}).
 *
 * <p>Allows player to fly as in creative. Mostly used by flying (captain)
 * morphs.</p>
 *
 * <p>API translation: 1.12 {@code player.capabilities} →
 * {@link PlayerEntity#getAbilities()} ({@code allowFlying}/{@code flying} are
 * still public fields); {@code sendPlayerAbilities()} →
 * {@link PlayerEntity#sendAbilitiesUpdate()}.</p>
 *
 * <p>Quirk preserved: {@code onDemorph} does <b>not</b> revoke flight from a
 * creative player. Spectators are creative-flagged through
 * {@code isCreative()} only on the server-side {@code ServerPlayerEntity} —
 * legacy had the same hole and the ability is a singleton, so this is a verbatim
 * port.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/Fly.java
 */
public class Fly extends Ability
{
    @Override
    public void onMorph(LivingEntity target)
    {
        if (target instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) target;

            if (!player.getAbilities().allowFlying)
            {
                player.getAbilities().allowFlying = true;
                player.sendAbilitiesUpdate();
            }
        }
    }

    @Override
    public void update(LivingEntity target)
    {
        this.onMorph(target);
    }

    @Override
    public void onDemorph(LivingEntity target)
    {
        if (target instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity) target;

            if (!player.isCreative())
            {
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
            }
        }
    }
}
