package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.RayTracing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/**
 * Interact-with-entity action (auto id 22, no extra payload).
 *
 * <p>Playback swaps the actor's rotation to the recorded frame's, raytraces 5
 * blocks for a target, restores the rotation, then right-clicks the target
 * through a player ({@code EntityPlayer.interactOn} → yarn
 * {@link PlayerEntity#interact(Entity, Hand)}).</p>
 *
 * <p>The interaction player is the shared
 * {@link Action#resolvePlayer(LivingEntity)} — legacy
 * {@code actor instanceof EntityPlayer ? (EntityPlayer) actor :
 * ((EntityActor) actor).fakePlayer}. One deliberate departure: a resolution
 * miss returns {@code null} and skips the interaction where legacy threw a
 * {@code ClassCastException}. The raytrace still runs either way (it is
 * side-effect free), because legacy performed it before touching the
 * player.</p>
 */
public class InteractEntityAction extends ItemUseAction
{
    public InteractEntityAction()
    {}

    public InteractEntityAction(Hand hand)
    {
        super(hand);
    }

    @Override
    public void apply(LivingEntity actor)
    {
        RecordPlayer record = EntityUtils.getRecordPlayer(actor);

        if (record == null)
        {
            return;
        }

        PlayerEntity player = resolvePlayer(actor);
        Frame frame = record.getCurrentFrame();

        if (frame == null)
        {
            return;
        }

        /* Temporarily swap the actor's rotation to the recorded frame's so the
         * raytrace aims where the recording looked, then restore. */
        float yaw = actor.getYaw();
        float pitch = actor.getPitch();
        float yawHead = actor.getHeadYaw();

        actor.setYaw(frame.yaw);
        actor.setPitch(frame.pitch);
        actor.setHeadYaw(frame.yawHead);

        Entity target = RayTracing.getTargetEntity(actor, 5.0);

        actor.setYaw(yaw);
        actor.setPitch(pitch);
        actor.setHeadYaw(yawHead);

        if (player == null)
        {
            return;
        }

        if (player != actor)
        {
            this.copyActor(actor, player, frame);
        }

        if (target != null)
        {
            player.interact(target, this.hand);
        }
    }

}
