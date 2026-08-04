package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.RayTracing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * Mounting action
 *
 * This actor makes actor to mount or unmount an entity by UUID.
 * I should probably move from UUID to using a type of entity with given
 * radius (3-5 blocks).
 */
public class MountingAction extends Action
{
    /**
     * Default UUID
     */
    public static final UUID DEFAULT = new UUID(0, 0);

    public UUID target = DEFAULT;
    public boolean isMounting;

    public MountingAction()
    {}

    public MountingAction(UUID target, boolean isMounting)
    {
        this.target = target;
        this.isMounting = isMounting;
    }

    /**
     * Look the mount up by UUID; when it is gone (or the recording predates a
     * stored UUID), fall back to a 5-block raytrace along the <b>current
     * playback frame's</b> rotation.
     *
     * <p>Legacy quirks preserved verbatim:</p>
     * <ul>
     * <li>the early-out is {@code mount == null && isMounting} — a
     * <b>dismount</b> with no resolvable target still dismounts, so an actor is
     * never left stuck on a vehicle;</li>
     * <li>a null current frame aborts the whole action, including the dismount,
     * but only on the UUID-miss path (legacy read the frame inside that
     * branch);</li>
     * <li>the rotation swap around the raytrace is restored afterwards.</li>
     * </ul>
     *
     * <p>1.20.4 mapping: {@code EntityUtils.entityByUUID} now rides
     * {@code ServerWorld.getEntity(UUID)}; {@code dismountRidingEntity()} →
     * {@link net.minecraft.entity.Entity#stopRiding()}.</p>
     */
    @Override
    public void apply(LivingEntity actor)
    {
        Entity mount = EntityUtils.entityByUUID(actor.getWorld(), this.target);

        if (mount == null)
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

            float yaw = actor.getYaw();
            float pitch = actor.getPitch();
            float yawHead = actor.getHeadYaw();

            actor.setYaw(frame.yaw);
            actor.setPitch(frame.pitch);
            actor.setHeadYaw(frame.yawHead);

            mount = RayTracing.getTargetEntity(actor, 5.0);

            actor.setYaw(yaw);
            actor.setPitch(pitch);
            actor.setHeadYaw(yawHead);
        }

        if (skipsWithoutTarget(mount, this.isMounting))
        {
            return;
        }

        if (this.isMounting)
        {
            actor.startRiding(mount);
        }
        else
        {
            actor.stopRiding();
        }
    }

    /**
     * The legacy asymmetric early-out: a <b>mount</b> with no resolvable target
     * is dropped, a <b>dismount</b> with no target still runs (so an actor is
     * never left stuck on a vehicle). Extracted for headless parity tests.
     */
    static boolean skipsWithoutTarget(Entity mount, boolean isMounting)
    {
        return mount == null && isMounting;
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.target = new UUID(buf.readLong(), buf.readLong());
        this.isMounting = buf.readBoolean();
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeLong(this.target.getMostSignificantBits());
        buf.writeLong(this.target.getLeastSignificantBits());
        buf.writeBoolean(this.isMounting);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.target = new UUID(tag.getLong("Most"), tag.getLong("Least"));
        this.isMounting = tag.getBoolean("Mounting");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putLong("Most", this.target.getMostSignificantBits());
        tag.putLong("Least", this.target.getLeastSignificantBits());
        tag.putBoolean("Mounting", this.isMounting);
    }
}
