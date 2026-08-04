package mchorse.metamorph.api.events;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Morph event (roadmap P56).
 *
 * <p>{@link MorphEvent.Pre} occurs when a player gets morphed or demorphed. If
 * the player gets demorphed then {@link #morph} is null. Check the player's
 * world {@code isClient} flag to see on which side this event is triggered.</p>
 *
 * <p>{@link MorphEvent.Pre} is cancelable: fire it through
 * {@link mchorse.metamorph.api.MetamorphEvents#MORPH_PRE} and if the invoker
 * returns {@code true} the player won't demorph or morph. Handlers may reassign
 * {@link #morph} (the caller then applies the reassigned morph, or demorphs if
 * set to null) and may flip {@link #force} — the post-cancel code MUST read
 * {@code event.morph}/{@code event.force}, not the originals (legacy
 * {@code MorphAPI.morph} does exactly this).</p>
 *
 * <p>{@link MorphEvent.Post} is fired after a player successfully morphs or
 * demorphs; it is informational.</p>
 *
 * <p>Port note: the legacy classes extended Forge's {@code Event} and carried a
 * {@code @Cancelable} annotation on {@code Pre}; here cancellation is expressed
 * by the {@link mchorse.metamorph.api.MetamorphEvents#MORPH_PRE} invoker's
 * boolean return, and the mutable fields on this object preserve the legacy
 * "handler replaces the morph / flips force" behavior.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/MorphEvent.java
 */
public abstract class MorphEvent
{
    public PlayerEntity player;
    public AbstractMorph morph;
    public boolean force;

    public MorphEvent(PlayerEntity player, AbstractMorph morph, boolean force)
    {
        this.player = player;
        this.morph = morph;
        this.force = force;
    }

    /**
     * Whether the given player is about to demorph.
     */
    public boolean isDemorphing()
    {
        return this.morph == null;
    }

    /**
     * Fires before a player morphs. Cancelable through
     * {@link mchorse.metamorph.api.MetamorphEvents#MORPH_PRE}.
     */
    public static class Pre extends MorphEvent
    {
        public Pre(PlayerEntity player, AbstractMorph morph, boolean force)
        {
            super(player, morph, force);
        }
    }

    /**
     * Fires after a player successfully morphed.
     */
    public static class Post extends MorphEvent
    {
        public Post(PlayerEntity player, AbstractMorph morph, boolean force)
        {
            super(player, morph, force);
        }
    }
}
