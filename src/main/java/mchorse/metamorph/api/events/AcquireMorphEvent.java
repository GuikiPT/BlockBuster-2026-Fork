package mchorse.metamorph.api.events;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Acquire morph event (roadmap P56).
 *
 * <p>{@link AcquireMorphEvent.Pre} is fired when a player is about to acquire a
 * morph. This does not necessarily mean the player already has this morph — use
 * {@link #hasMorph()} to figure out whether the player already owns it.</p>
 *
 * <p>Handlers may modify {@link #morph} (the player then acquires a different
 * morph). Canceling (the {@link mchorse.metamorph.api.MetamorphEvents#ACQUIRE_MORPH_PRE}
 * invoker returns {@code true}) prevents acquisition — though if the player
 * already has the morph the cancel is effectively useless.</p>
 *
 * <p>{@link AcquireMorphEvent.Post} is fired after a player successfully
 * acquires a new morph.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/AcquireMorphEvent.java
 */
public abstract class AcquireMorphEvent
{
    public PlayerEntity player;
    public AbstractMorph morph;

    public AcquireMorphEvent(PlayerEntity player, AbstractMorph morph)
    {
        this.player = player;
        this.morph = morph;
    }

    /**
     * Does the given player already have this morph?
     *
     * <p>SEAM(P52): legacy delegated to
     * {@code Morphing.get(this.player).acquiredMorph(this.morph)}. The
     * {@code Morphing} capability / {@code MorphingStorage} is owned by P52 and
     * is not in this tree yet, so this returns {@code false} as a placeholder.
     * When P52 lands, replace the body with the delegation above (the firing
     * owner is {@code MorphAPI}, P48, which is likewise deferred).</p>
     */
    public boolean hasMorph()
    {
        return false;
    }

    /**
     * Fires before a player acquires a morph. Cancelable through
     * {@link mchorse.metamorph.api.MetamorphEvents#ACQUIRE_MORPH_PRE}.
     */
    public static class Pre extends AcquireMorphEvent
    {
        public Pre(PlayerEntity player, AbstractMorph morph)
        {
            super(player, morph);
        }
    }

    /**
     * Fires after a player successfully acquires a morph.
     */
    public static class Post extends AcquireMorphEvent
    {
        public Post(PlayerEntity player, AbstractMorph morph)
        {
            super(player, morph);
        }
    }
}
