package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

/**
 * Swipe action
 *
 * Swipes actor's hand.
 */
public class SwipeAction extends Action
{
    public SwipeAction()
    {}

    @Override
    public void apply(LivingEntity actor)
    {
        RecordPlayer player = EntityUtils.getRecordPlayer(actor);

        actor.swingHand(Hand.MAIN_HAND, true);

        /* Hack to swing the arm for the real player (vanilla doesn't echo your
         * own swing back to you) — legacy sent SPacketAnimation(actor, 0),
         * which maps to EntityAnimationS2CPacket with SWING_MAIN_HAND. */
        if (player != null && player.realPlayer && player.actor instanceof ServerPlayerEntity serverPlayer)
        {
            serverPlayer.networkHandler.sendPacket(new EntityAnimationS2CPacket(serverPlayer, EntityAnimationS2CPacket.SWING_MAIN_HAND));
        }

        if (Blockbuster.actorSwishSwipe.get())
        {
            actor.getWorld().playSound(null, actor.getX(), actor.getY(), actor.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, actor.getSoundCategory(), 1.0F, 1.0F);
        }
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
