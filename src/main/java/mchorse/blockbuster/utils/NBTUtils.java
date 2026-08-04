package mchorse.blockbuster.utils;

import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.IGunItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Partial port of Blockbuster 2.7.2's NBTUtils (roadmap P14). The BlockPos
 * suffixed-key convention landed with P14; the GunProps half
 * ({@code saveGunProps}/{@code getGunProps}, NBT key "Gun") lands with P193.
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/utils/NBTUtils.java
 */
public class NBTUtils
{
    /* GunProps ("Gun" stack-tag contract) */

    /**
     * Write a serialised {@link GunProps} compound onto the stack's tag under
     * key {@code "Gun"}. Returns {@code false} for a non-gun item (leaving the
     * stack untouched), {@code true} once the tag is stored.
     *
     * <p>Gun identification is gated on {@link IGunItem} (the concrete
     * {@code ItemGun} port, P194, must implement it) rather than the legacy
     * {@code instanceof ItemGun}, so P193 does not depend on the parallel
     * behaviour branch.</p>
     */
    public static boolean saveGunProps(ItemStack stack, NbtCompound tag)
    {
        if (!(stack.getItem() instanceof IGunItem))
        {
            return false;
        }

        /* 1.20.4's hasNbt() reports an empty compound as absent (unlike
         * 1.12's hasTagCompound()), so use getOrCreateNbt() to guarantee the
         * backing tag exists before writing the "Gun" sub-tag. */
        stack.getOrCreateNbt().put("Gun", tag);

        return true;
    }

    /**
     * Read the {@link GunProps} carried by a gun stack. Contract (byte-exact
     * with 1.12.2): a non-gun item yields {@code null}; a gun stack with a
     * {@code "Gun"} tag yields the parsed props; a gun stack with no such tag
     * yields a <b>fresh default</b> {@link GunProps} — never {@code null} for a
     * gun item.
     */
    public static GunProps getGunProps(ItemStack stack)
    {
        if (!(stack.getItem() instanceof IGunItem))
        {
            return null;
        }

        NbtCompound tag = stack.getNbt();

        if (tag != null && tag.contains("Gun"))
        {
            return new GunProps(tag.getCompound("Gun"));
        }

        return new GunProps();
    }

    /* BlockPos */

    /**
     * Save given {@link BlockPos} into {@link NbtCompound} tag
     */
    public static void saveBlockPos(String key, NbtCompound tag, BlockPos pos)
    {
        tag.putInt(key + "X", pos.getX());
        tag.putInt(key + "Y", pos.getY());
        tag.putInt(key + "Z", pos.getZ());
    }

    /**
     * Get {@link BlockPos} position from {@link NbtCompound} tag
     */
    public static BlockPos getBlockPos(String key, NbtCompound tag)
    {
        String x = key + "X";
        String y = key + "Y";
        String z = key + "Z";

        if (tag == null || !tag.contains(x) || !tag.contains(y) || !tag.contains(z))
        {
            return null;
        }

        return new BlockPos(tag.getInt(x), tag.getInt(y), tag.getInt(z));
    }
}
