package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Hotbar slot content change action.
 *
 * <p>Conditional NBT keys ({@code Slot} only when != -1, {@code ItemStack}
 * only when non-null) are byte-parity relevant.</p>
 */
public class HotbarChangeAction extends Action
{
    private int slotToChange = -1;
    private NbtCompound newItemStack;

    public HotbarChangeAction()
    {
        this.newItemStack = new NbtCompound();
    }

    public HotbarChangeAction(int slotToChange, ItemStack newItemStack)
    {
        this();
        this.slotToChange = slotToChange;

        if (newItemStack != null)
        {
            this.newItemStack = newItemStack.writeNbt(new NbtCompound());
        }
    }

    public int getSlot()
    {
        return this.slotToChange;
    }

    public void setSlot(int slot)
    {
        this.slotToChange = slot;
    }

    public ItemStack getItemStack()
    {
        /* Read side: legacy pre-flattening item NBT routes through the P71
         * shim; already-modern captures pass through unchanged. */
        return LegacyIdMap.itemStack(this.newItemStack);
    }

    public void setItemStack(ItemStack itemStack)
    {
        if (itemStack != null)
        {
            this.newItemStack = itemStack.writeNbt(new NbtCompound());
        }
    }

    @Override
    public void apply(LivingEntity entity)
    {
        if (entity instanceof PlayerEntity && this.slotToChange != -1)
        {
            PlayerEntity player = (PlayerEntity) entity;

            /* Legacy pre-flattening item NBT routes through the P71 shim;
             * already-modern captures pass through unchanged. */
            player.getInventory().setStack(this.slotToChange, LegacyIdMap.itemStack(this.newItemStack));
        }
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.slotToChange = buf.readInt();
        this.newItemStack = NBTUtils.readInfiniteTag(buf);
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);

        buf.writeInt(this.slotToChange);
        buf.writeNbt(this.newItemStack);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.slotToChange = tag.contains("Slot") ? tag.getInt("Slot") : this.slotToChange;

        if (tag.contains("ItemStack"))
        {
            this.newItemStack = tag.getCompound("ItemStack");
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        if (this.slotToChange != -1)
        {
            tag.putInt("Slot", this.slotToChange);
        }

        if (this.newItemStack != null)
        {
            tag.put("ItemStack", this.newItemStack);
        }
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
