package mchorse.blockbuster.recording.actions;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Close-container action (auto id 23, no payload).
 */
public class CloseContainerAction extends Action
{
    /**
     * Legacy {@code EntityPlayer player = actor instanceof EntityActor ?
     * ((EntityActor) actor).fakePlayer : (EntityPlayer) actor;} then
     * {@code closeScreen()} when a non-inventory container is open on the
     * server — routed through {@link Action#resolvePlayer(LivingEntity)}, so an
     * actor closes the container its
     * {@link mchorse.blockbuster.common.entity.EntityActor.EntityFakePlayer}
     * opened.
     */
    @Override
    public void apply(LivingEntity actor)
    {
        PlayerEntity player = Action.resolvePlayer(actor);

        if (player != null
            && !player.getWorld().isClient
            && player.currentScreenHandler != player.playerScreenHandler)
        {
            player.closeHandledScreen();
        }
    }
}
