package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;

/**
 * Morph's action action (roadmap P107, byte id via {@code ActionRegistry}).
 *
 * <p>Executes the current morph's own action ({@code morph.action(actor)}) with
 * the actor's rotations temporarily forced to the recorded frame's, so a morph
 * whose action depends on where it is looking (a gun's shot direction, say)
 * fires along the recorded aim rather than the interpolated live rotation. The
 * originals are restored afterwards.</p>
 *
 * <p><b>Legacy bug preserved:</b> the saved {@code yawHead} local reads
 * {@code actor.rotationYaw}, <em>not</em> the head yaw — so the restore writes
 * body yaw into head yaw and slightly corrupts it. Recordings were made against
 * this behavior; do not "fix" it.</p>
 */
public class MorphActionAction extends Action
{
    public MorphActionAction()
    {}

    @Override
    public void apply(LivingEntity actor)
    {
        AbstractMorph morph = mchorse.metamorph.api.EntityUtils.getMorph(actor);

        if (morph == null)
        {
            return;
        }

        Frame frame = EntityUtils.getRecordPlayer(actor).getCurrentFrame();

        if (frame == null)
        {
            return;
        }

        float yaw = actor.getYaw();
        /* Legacy bug, preserved: reads yaw, not head yaw. */
        float yawHead = actor.getYaw();
        float pitch = actor.getPitch();

        float prevYaw = actor.prevYaw;
        float prevYawHead = actor.prevHeadYaw;
        float prevPitch = actor.prevPitch;

        actor.setYaw(frame.yaw);
        actor.prevYaw = frame.yaw;
        actor.headYaw = actor.prevHeadYaw = frame.yawHead;
        actor.setPitch(frame.pitch);
        actor.prevPitch = frame.pitch;

        morph.action(actor);

        actor.setYaw(yaw);
        actor.headYaw = yawHead;
        actor.setPitch(pitch);

        actor.prevYaw = prevYaw;
        actor.prevHeadYaw = prevYawHead;
        actor.prevPitch = prevPitch;
    }
}
