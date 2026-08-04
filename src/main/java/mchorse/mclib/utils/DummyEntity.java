package mchorse.mclib.utils;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.EnumMap;

/**
 * Dummy entity (roadmap P84).
 *
 * <p>Port of McLib 2.4.3's {@code mchorse.mclib.utils.DummyEntity}. Used in the
 * model editor / morph picker viewports ({@code
 * mchorse.mclib.client.gui.framework.elements.GuiModelRenderer}) as a player
 * substitution so the model's living-animation / held-item methods have a host
 * entity to read.</p>
 *
 * <p>It lives in the <b>common</b> source set to keep McLib's own package
 * layout, even though every consumer is client-side. Nothing here touches a
 * client-only class (P54 pass 6).</p>
 *
 * <p>1.20.4 mappings: legacy extended {@code EntityLivingBase} directly (1.12
 * allowed type-less custom living entities); yarn's {@link LivingEntity}
 * requires an {@link EntityType} + {@link World}, both injected by the P84
 * factory installed in {@code BlockbusterClient} (an armor-stand type over the
 * client world — nothing about the stand behaviour is used, only the living
 * host). Equipment is array-backed by an {@link EnumMap} keyed on
 * {@link EquipmentSlot} (legacy used a 6-slot {@code ItemStack[]} indexed by
 * {@code EntityEquipmentSlot.getSlotIndex()}); {@link #toggleItems(boolean)}
 * equips the same <b>diamond sword (right) / golden sword (left)</b> defaults
 * for the held-item pose preview.</p>
 */
public class DummyEntity extends LivingEntity
{
    private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);

    public ItemStack right;
    public ItemStack left;

    public DummyEntity(EntityType<? extends LivingEntity> type, World worldIn)
    {
        super(type, worldIn);

        this.right = new ItemStack(Items.DIAMOND_SWORD);
        this.left = new ItemStack(Items.GOLDEN_SWORD);

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            this.equipment.put(slot, ItemStack.EMPTY);
        }
    }

    public void setItems(ItemStack left, ItemStack right)
    {
        this.left = left;
        this.right = right;
    }

    public void toggleItems(boolean toggle)
    {
        if (toggle)
        {
            this.equipment.put(EquipmentSlot.MAINHAND, this.right);
            this.equipment.put(EquipmentSlot.OFFHAND, this.left);
        }
        else
        {
            this.equipment.put(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.equipment.put(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    @Override
    public Iterable<ItemStack> getArmorItems()
    {
        return Arrays.asList(
            this.equipment.get(EquipmentSlot.FEET),
            this.equipment.get(EquipmentSlot.LEGS),
            this.equipment.get(EquipmentSlot.CHEST),
            this.equipment.get(EquipmentSlot.HEAD)
        );
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        ItemStack stack = this.equipment.get(slot);

        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.put(slot, stack);
    }

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }
}
