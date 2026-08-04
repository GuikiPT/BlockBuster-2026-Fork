package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.RayTracing;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Attack action
 *
 * This action is responsible for attacking an entity in in front of the actor.
 *
 * <p>Quirk: {@code fromNBT} keeps the 2F default when the {@code Damage} key
 * is absent — do not "normalize" to the parent's unconditional read.</p>
 */
public class AttackAction extends DamageAction
{
    public AttackAction()
    {
        this.damage = 2F;
    }

    public AttackAction(float damage)
    {
        super(damage);
    }

    @Override
    public void apply(LivingEntity actor)
    {
        RecordPlayer player = EntityUtils.getRecordPlayer(actor);

        if (player == null)
        {
            return;
        }

        Frame frame = player.getCurrentFrame();

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

        if (target != null)
        {
            target.damage(actor.getDamageSources().mobAttack(actor), this.damage);

            AbstractMorph morph = mchorse.metamorph.api.EntityUtils.getMorph(actor);

            if (morph != null)
            {
                morph.attack(target, actor);
            }
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("Damage"))
        {
            this.damage = tag.getFloat("Damage");
        }
    }
}
