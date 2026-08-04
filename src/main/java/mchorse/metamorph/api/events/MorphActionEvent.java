package mchorse.metamorph.api.events;

import mchorse.metamorph.api.abilities.IAction;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Morph action event (roadmap P56).
 *
 * <p>Fired when a player uses an action on the server side (through
 * {@link mchorse.metamorph.api.MetamorphEvents#MORPH_ACTION}). It is neither
 * cancelable nor modifiable — purely informational.</p>
 *
 * <p>Legacy fired this on {@code MinecraftForge.EVENT_BUS}; here it is dispatched
 * through the bundled {@link mchorse.metamorph.api.MetamorphEvents} bus.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/events/MorphActionEvent.java
 */
public class MorphActionEvent
{
    public PlayerEntity player;
    public IAction action;
    public AbstractMorph morph;

    public MorphActionEvent(PlayerEntity player, IAction action, AbstractMorph morph)
    {
        this.player = player;
        this.action = action;
        this.morph = morph;
    }

    /**
     * Check whether this action is valid (and took place — if the action is
     * null, nothing happens).
     */
    public boolean isValid()
    {
        return this.action != null;
    }
}
