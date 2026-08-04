package mchorse.metamorph.entity;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.PlayerEntity;

/**
 * S22/P242 — installs {@link SoundHandler#provider}, the seam P54.2 declared and
 * the P52 morphing component never filled.
 *
 * <p>Consequence while it was {@code null}: {@code SoundHandler.currentMorph}
 * answered {@code null} for every player, so all three
 * {@code PlayerEntitySoundMixin} injections bailed on their first line — a
 * morphed player kept making <i>player</i> hurt, death and step sounds, and the
 * anti-phantom-step hitbox suppression never ran either. The mixins were live;
 * the lookup behind them was not.</p>
 *
 * <p>{@link SoundHandler#GENERIC_DAMAGE} is deliberately <b>not</b> installed
 * here — see its javadoc. 1.20.4 damage sources are bound to a world's dynamic
 * registry, so there is no constant to assign at init; it is captured lazily off
 * the hurt entity the first time a caller actually needs the fallback, which for
 * the production call graph is never (the {@code getHurtSound(DamageSource)}
 * injection always carries the real source).</p>
 */
public final class SoundHandlerWiring
{
    private SoundHandlerWiring()
    {}

    /** Idempotent. */
    public static void install()
    {
        SoundHandler.provider = SoundHandlerWiring::currentMorph;
    }

    /**
     * Legacy {@code Metamorph.proxy.getCapability(player).getCurrentMorph()}
     * — total: a player without the component (or a bare
     * {@code PlayerEntity} built outside the mixin environment) is simply not
     * morphed.
     */
    public static AbstractMorph currentMorph(PlayerEntity player)
    {
        IMorphing morphing = player == null ? null : Morphing.get(player);

        return morphing == null ? null : morphing.getCurrentMorph();
    }
}
