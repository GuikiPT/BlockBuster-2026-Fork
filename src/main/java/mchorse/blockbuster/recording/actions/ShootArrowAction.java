package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.BowItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.World;

/**
 * Shoot arrow action
 *
 * This action shoots emulates arrow shooting. This would look confusing when
 * the actor lack of bow, he would be like freaking arrow mage or something.
 *
 * <p>Quirk: {@code charge} is an int in memory, a <b>byte</b> on disk and
 * wire.</p>
 */
public class ShootArrowAction extends Action
{
    public int charge;

    public ShootArrowAction()
    {}

    public ShootArrowAction(int charge)
    {
        this.charge = charge;
    }

    /**
     * Some code in this method is borrowed from ItemBow, I guess, I don't
     * remember (legacy comment kept).
     *
     * <p>1.20.4 mapping: {@code EntityTippedArrow(world, actor)} → an
     * {@link ArrowEntity} built from a plain {@code minecraft:arrow} stack
     * (1.12's tipped arrow with no potion tag <i>is</i> a plain arrow);
     * {@code ItemBow.getArrowVelocity} → {@link BowItem#getPullProgress(int)};
     * {@code IProjectile.shoot(shooter, pitch, yaw, roll, velocity,
     * inaccuracy)} → {@code ProjectileEntity.setVelocity(Entity, float, float,
     * float, float, float)} (same operand order — verified with javap).</p>
     *
     * <p>Aim comes from the <b>current playback frame</b>, not the actor's live
     * rotation, and the {@code * 3.0F} velocity multiplier is legacy's.</p>
     */
    @Override
    public void apply(LivingEntity actor)
    {
        RecordPlayer record = EntityUtils.getRecordPlayer(actor);

        if (record == null)
        {
            return;
        }

        Frame frame = record.getCurrentFrame();

        if (frame == null)
        {
            return;
        }

        World world = actor.getWorld();
        /* 1.20.1's two-arg constructor already defaults the pickup stack to a
         * plain arrow, so there is no ItemStack overload to pass one to. */
        ArrowEntity arrow = new ArrowEntity(world, actor);

        arrow.setVelocity(actor, frame.pitch, frame.yaw, 0.0F, arrowVelocity(this.charge), 1.0F);
        world.spawnEntity(arrow);
    }

    /**
     * Legacy {@code ItemBow.getArrowVelocity(charge) * 3.0F} — the launch speed
     * handed to the projectile. Split out so the {@code * 3.0F} multiplier and
     * the 20-tick full-draw clamp can be pinned without a live world.
     */
    static float arrowVelocity(int charge)
    {
        return BowItem.getPullProgress(charge) * 3.0F;
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.charge = buf.readByte();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeByte(this.charge);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.charge = tag.getByte("Charge");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putByte("Charge", (byte) this.charge);
    }
}
