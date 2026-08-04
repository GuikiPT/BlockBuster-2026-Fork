package mchorse.vanilla_pack.morphs;

import java.util.Objects;

import mchorse.blockbuster.legacy.LegacyIdMap;
import mchorse.mclib.utils.NBTUtils;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;

/**
 * Item morph — disguise as an item/held stack (roadmap P53.2).
 *
 * <p>NBT: {@code Stack} (ItemStack, default {@code diamond_hoe}, omitted when
 * empty), {@code Transform} (<b>always written</b> — even an empty string —
 * unlike almost every other field; an item camera-transform name from the fixed
 * 9-entry frozen key set), {@code Texture} (2D-extrusion render path),
 * {@code Animation} (floating bob+spin), {@code ItemFromEquipment} +
 * {@code EquipmentSlot} (slot name, written only when not mainhand).</p>
 *
 * <p>The 9 {@code Transform} string keys ({@code none},
 * {@code third_person_left_hand}, {@code third_person_right_hand},
 * {@code first_person_left_hand}, {@code first_person_right_hand}, {@code head},
 * {@code gui}, {@code ground}, {@code fixed}) are a frozen disk contract; the
 * String is held here, and its mapping to yarn's {@code ModelTransformationMode}
 * lives in the P54 client renderer. Texture extrusion (ItemExtruder) is also
 * client-side (P54).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/ItemMorph.java
 */
public class ItemMorph extends ItemStackMorph
{
    public ItemStack stack = new ItemStack(Items.DIAMOND_HOE, 1);
    public String transform = "";
    public ResourceLocation texture;
    public boolean animation;
    public boolean itemFromEquipment;
    public EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;

    /* Raw legacy {@code Stack} NBT, preserved verbatim for byte-parity re-emit
     * (see BlockMorph's loadedBlockId/loadedMeta policy). Non-null exactly when
     * the render stack was loaded from disk; setStack (a modern GUI edit) clears
     * it so the modern stack is written instead. */
    protected NbtCompound loadedStack;

    public ItemMorph()
    {
        this.name = "item";
    }

    @Override
    public void setStack(ItemStack stack)
    {
        this.stack = stack;
        this.loadedStack = null;
    }

    @Override
    public ItemStack getStack()
    {
        return this.stack;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof ItemMorph)
        {
            ItemMorph item = (ItemMorph) obj;

            result = result && ItemStack.areEqual(this.stack, item.stack);
            result = result && Objects.equals(this.transform, item.transform);
            result = result && Objects.equals(this.texture, item.texture);
            result = result && this.animation == item.animation;
            result = result && this.itemFromEquipment == item.itemFromEquipment;
            result = result && this.equipmentSlot == item.equipmentSlot;
        }

        return result;
    }

    @Override
    public AbstractMorph create()
    {
        return new ItemMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ItemMorph)
        {
            ItemMorph item = (ItemMorph) from;

            this.stack = item.stack.copy();
            this.transform = item.transform;
            this.texture = item.texture;
            this.animation = item.animation;
            this.itemFromEquipment = item.itemFromEquipment;
            this.equipmentSlot = item.equipmentSlot;
            this.loadedStack = item.loadedStack == null ? null : NBTUtils.legacyCopy(item.loadedStack);
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return target.getWidth();
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return target.getHeight();
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.loadedStack != null)
        {
            /* Byte-parity: re-emit the raw legacy Stack NBT verbatim rather than
             * the shim-flattened render stack (mirrors BlockMorph). */
            tag.put("Stack", NBTUtils.legacyCopy(this.loadedStack));
        }
        else if (!this.stack.isEmpty())
        {
            tag.put("Stack", this.stack.writeNbt(new NbtCompound()));
        }

        /* Transform is always written — even when empty (legacy quirk). */
        tag.putString("Transform", this.transform);

        if (this.texture != null)
        {
            tag.putString("Texture", this.texture.toString());
        }

        if (this.animation)
        {
            tag.putBoolean("Animation", this.animation);
        }

        if (this.itemFromEquipment)
        {
            tag.putBoolean("ItemFromEquipment", this.itemFromEquipment);
        }

        if (this.equipmentSlot != EquipmentSlot.MAINHAND)
        {
            tag.putString("EquipmentSlot", this.equipmentSlot.getName());
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Stack"))
        {
            NbtCompound stackTag = tag.getCompound("Stack");

            /* P71: route the render stack through the central id-translation
             * shim so a legacy meta-subtype item (e.g. Stack:{id:minecraft:wool,
             * Damage:14} -> red_wool, dye@4 -> lapis_lazuli, record_cat ->
             * music_disc_cat) flattens instead of resolving to air. The raw
             * legacy NBT is kept in loadedStack and re-emitted verbatim on write
             * for byte-parity (mirrors BlockMorph). */
            this.loadedStack = NBTUtils.legacyCopy(stackTag);
            this.stack = LegacyIdMap.itemStack(stackTag);
        }

        this.transform = tag.getString("Transform");

        if (tag.contains("Texture"))
        {
            this.texture = RLUtils.create(tag.getString("Texture"));
        }

        if (tag.contains("Animation"))
        {
            this.animation = tag.getBoolean("Animation");
        }

        if (tag.contains("ItemFromEquipment"))
        {
            this.itemFromEquipment = tag.getBoolean("ItemFromEquipment");
        }

        if (tag.contains("EquipmentSlot"))
        {
            try
            {
                this.equipmentSlot = EquipmentSlot.byName(tag.getString("EquipmentSlot"));
            }
            catch (Exception e)
            {
                this.equipmentSlot = EquipmentSlot.MAINHAND;
            }
        }
    }
}
