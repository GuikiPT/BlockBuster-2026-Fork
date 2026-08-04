package mchorse.metamorph.api.events;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Morph ghost spawn event (roadmap P56).
 *
 * <p>{@link SpawnGhostEvent.Pre} occurs when a player kills a monster and a
 * ghost is about to spawn from it. It does not occur if the config option
 * {@code prevent_kill_acquire} is set, nor if {@code prevent_ghosts} is set
 * while the player already has the morph.</p>
 *
 * <p>{@link #player} is the player responsible for the kill. {@link #morph} is
 * the morph representing the killed monster — handlers may replace it (a
 * different ghost spawns) or set it to {@code null} (no ghost spawns; legacy
 * null-check). Canceling (the
 * {@link mchorse.metamorph.api.MetamorphEvents#SPAWN_GHOST_PRE} invoker returns
 * {@code true}) prevents the ghost from spawning.</p>
 *
 * <p>{@link SpawnGhostEvent.Post} is fired after the ghost successfully
 * spawns.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/SpawnGhostEvent.java
 */
public abstract class SpawnGhostEvent
{
    public PlayerEntity player;
    public AbstractMorph morph;

    public SpawnGhostEvent(PlayerEntity player, AbstractMorph morph)
    {
        this.player = player;
        this.morph = morph;
    }

    /**
     * Fires before a ghost spawns. Cancelable through
     * {@link mchorse.metamorph.api.MetamorphEvents#SPAWN_GHOST_PRE}.
     */
    public static class Pre extends SpawnGhostEvent
    {
        public Pre(PlayerEntity player, AbstractMorph morph)
        {
            super(player, morph);
        }
    }

    /**
     * Fires after a ghost successfully spawns.
     */
    public static class Post extends SpawnGhostEvent
    {
        public Post(PlayerEntity player, AbstractMorph morph)
        {
            super(player, morph);
        }
    }
}
