package mchorse.blockbuster.recording.actions;

import java.util.Random;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;

/**
 * Item drop action
 *
 * Actor tosses held item away just like player when pressing "q" key.
 *
 * <p>The item is stored as the raw legacy ItemStack NBT compound (key
 * {@code Data}) — pre-flattening ids inside it are translated by the P71 id
 * shim only when applied, never rewritten on disk.</p>
 */
public class DropAction extends Action
{
    public NbtCompound itemData;

    public DropAction()
    {
        this.itemData = new NbtCompound();
    }

    public DropAction(ItemStack item)
    {
        this();

        item.writeNbt(this.itemData);
    }

    /**
     * Replicates {@code EntityPlayer.dropItem} math exactly, aiming with the
     * <b>current playback frame's</b> yaw/pitch (drops go where the recording
     * looked, not where the actor currently faces).
     */
    @Override
    public void apply(LivingEntity actor)
    {
        if (this.itemData == null)
        {
            return;
        }

        RecordPlayer player = EntityUtils.getRecordPlayer(actor);

        if (player == null)
        {
            return;
        }

        Frame frame = player.getCurrentFrame();

        /* Legacy pre-flattening item NBT routes through the P71 shim; already-
         * modern captures pass through unchanged. */
        ItemStack items = LegacyIdMap.itemStack(this.itemData);

        if (frame == null)
        {
            return;
        }

        ItemEntity item = new ItemEntity(actor.getWorld(), actor.getX(), actor.getEyeY() - 0.3D, actor.getZ(), items);

        item.setPickupDelay(40);

        double[] motion = computeMotion(frame.yaw, frame.pitch, new Random());

        item.setVelocity(motion[0], motion[1], motion[2]);

        actor.getWorld().spawnEntity(item);
    }

    /**
     * The exact {@code EntityPlayer.dropItem} throw-velocity math, split out so
     * the golden test can pin the operand order without a live world.
     *
     * <p>Random-call order is load-bearing (four {@code nextFloat()} draws:
     * two for the spread angle {@code f1}, two for the vertical jitter) — do not
     * reorder.</p>
     *
     * @return {@code {motionX, motionY, motionZ}}
     */
    public static double[] computeMotion(float yaw, float pitch, Random rand)
    {
        final float PI = 3.1415927F;

        float f = 0.3F;

        double motionX = -MathHelper.sin(yaw / 180.0F * PI) * MathHelper.cos(pitch / 180.0F * PI) * f;
        double motionZ = MathHelper.cos(yaw / 180.0F * PI) * MathHelper.cos(pitch / 180.0F * PI) * f;
        double motionY = -MathHelper.sin(pitch / 180.0F * PI) * f + 0.1F;

        f = 0.02F;
        float f1 = rand.nextFloat() * PI * 2.0F * rand.nextFloat();

        motionX += Math.cos(f1) * f;
        motionY += (rand.nextFloat() - rand.nextFloat()) * 0.1F;
        motionZ += Math.sin(f1) * f;

        return new double[] {motionX, motionY, motionZ};
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.itemData = NBTUtils.readInfiniteTag(buf);
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);
        buf.writeNbt(this.itemData);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.itemData = tag.getCompound("Data");
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.put("Data", this.itemData);
    }
}
