package mchorse.blockbuster.recording.actions;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.mclib.utils.NBTUtils;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * Equip item action
 *
 * This action equips an item from replay to the actor, so he either equips the
 * item into one of the hands or in of the armor slots (shoes, leggins, chestplate,
 * or helmet)
 *
 * This action is also called to "de-equip" an item from equipment
 */
public class EquipAction extends Action
{
    public byte armorSlot;
    public NbtCompound itemData;
    private byte hotbarSlot = -1;

    public EquipAction()
    {
        this.itemData = new NbtCompound();
    }

    public EquipAction(byte armorSlot, ItemStack item)
    {
        this();
        this.armorSlot = armorSlot;

        if (item != null)
        {
            item.writeNbt(this.itemData);
        }
    }

    public EquipAction(byte armorSlot, byte hotbarSlot, ItemStack item)
    {
        this(armorSlot, item);

        this.hotbarSlot = hotbarSlot;
    }

    @Override
    public void apply(LivingEntity actor)
    {
        EquipmentSlot slot = this.getSlotByIndex(this.armorSlot);

        if (slot == null)
        {
            return;
        }

        if (this.itemData == null)
        {
            this.updateCurrentItemIndex(actor, slot);

            actor.equipStack(slot, ItemStack.EMPTY);
        }
        else
        {
            this.updateCurrentItemIndex(actor, slot);

            /* Legacy pre-flattening item NBT (id + Damage subtype) routes
             * through the P71 shim; already-modern captures pass through. */
            actor.equipStack(slot, LegacyIdMap.itemStack(this.itemData));
        }
    }

    /**
     * The currentItem index in the inventory can be delayed (client shows
     * different current slot than what is on server); syncing it before a
     * MAINHAND equip avoids inventory corruption.
     */
    private void updateCurrentItemIndex(LivingEntity entity, EquipmentSlot slot)
    {
        if (entity instanceof PlayerEntity && this.hotbarSlot != -1 && slot == EquipmentSlot.MAINHAND)
        {
            ((PlayerEntity) entity).getInventory().selectedSlot = this.hotbarSlot;
        }
    }

    /**
     * Legacy matched {@code EntityEquipmentSlot.getSlotIndex()} — the
     * serialized byte stays in that legacy index space (0=main hand,
     * 1=feet, 2=legs, 3=chest, 4=head, 5=off hand), which yarn's
     * {@link EquipmentSlot#getArmorStandSlotId()} reproduces one-to-one
     * (see the capture side in {@code PlayerTracker}: mainhand→0, armor
     * slots→1..4, offhand→5).
     */
    private EquipmentSlot getSlotByIndex(int index)
    {
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            if (slot.getArmorStandSlotId() == index) return slot;
        }

        return null;
    }

    @Override
    public void fromBuf(PacketByteBuf buf)
    {
        super.fromBuf(buf);
        this.armorSlot = buf.readByte();
        this.hotbarSlot = buf.readByte();
        this.itemData = NBTUtils.readInfiniteTag(buf);
    }

    @Override
    public void toBuf(PacketByteBuf buf)
    {
        super.toBuf(buf);

        buf.writeByte(this.armorSlot);
        buf.writeByte(this.hotbarSlot);
        buf.writeNbt(this.itemData);
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.armorSlot = tag.getByte("Slot");
        this.hotbarSlot = tag.contains("HotbarSlot") ? tag.getByte("HotbarSlot") : this.hotbarSlot;

        if (tag.contains("Data"))
        {
            this.itemData = tag.getCompound("Data");
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        tag.putByte("Slot", this.armorSlot);

        if (this.hotbarSlot != -1)
        {
            tag.putByte("HotbarSlot", this.hotbarSlot);
        }

        if (this.itemData != null)
        {
            tag.put("Data", this.itemData);
        }
    }

    @Override
    public boolean isSafe()
    {
        return true;
    }
}
