package mchorse.vanilla_pack.abilities;

import mchorse.metamorph.api.abilities.Ability;
import net.minecraft.entity.LivingEntity;

/**
 * Step Up ability (roadmap P49.1, registry id {@code step_up}).
 *
 * <p>This ability makes player walk up blocks, like a horse.</p>
 *
 * <p>API translation: 1.12's public {@code stepHeight} field is private in
 * 1.20.4 with a public {@link net.minecraft.entity.Entity#setStepHeight(float)}
 * setter (step height only becomes an attribute in 1.20.5 — not here).</p>
 *
 * <p><b>LEGACY BUG (load-bearing):</b> {@code onDemorph} restores a hard-coded
 * {@code 0.6F} instead of whatever the entity's step height was before morphing,
 * so demorphing while another mod raised the player's step height silently
 * resets it to vanilla. Kept, because 0.6 is also the value every other
 * Metamorph path assumes.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../vanilla_pack/abilities/StepUp.java
 */
public class StepUp extends Ability
{
    @Override
    public void update(LivingEntity target)
    {
        target.setStepHeight(1.0f);
    }

    @Override
    public void onMorph(LivingEntity target)
    {
        target.setStepHeight(1.0f);
    }

    @Override
    public void onDemorph(LivingEntity target)
    {
        target.setStepHeight(0.6f);
    }
}
