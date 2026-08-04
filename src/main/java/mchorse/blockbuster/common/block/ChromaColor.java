package mchorse.blockbuster.common.block;

import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.StringIdentifiable;

/**
 * P97: chroma (green screen) block colors.
 *
 * <p>Ported verbatim from {@code BlockGreen.ChromaColor} (Blockbuster 2.7.2).
 * The enum <b>order is a disk/wire contract</b>: it equals the legacy
 * blockstate metadata (0..7). Do not reorder — the S20 world migrator maps
 * legacy {@code meta -> color} by {@link #ordinal()} via {@link #byMeta(int)},
 * and the blockstate {@code color} property serializes through
 * {@link #asString()}.</p>
 */
public enum ChromaColor implements StringIdentifiable
{
    GREEN("green"), BLUE("blue"), RED("red"), YELLOW("yellow"), CYAN("cyan"), PURPLE("purple"), WHITE("white"), BLACK("black");

    /**
     * Lowercase serialized name (legacy {@code IStringSerializable.getName()}).
     * Kept as a public field named {@code name} for diff-ability against the
     * legacy source.
     */
    public final String name;

    private ChromaColor(String name)
    {
        this.name = name;
    }

    @Override
    public String asString()
    {
        return this.name;
    }

    /**
     * Legacy {@code BlockGreen.getStateFromMeta}: clamp out-of-range metadata
     * (old/corrupted worlds) to {@link #GREEN}, silently. The S20 migrator must
     * do the same so chroma blocks never fail to load.
     */
    public static ChromaColor byMeta(int meta)
    {
        ChromaColor[] values = values();

        if (meta < 0 || meta >= values.length)
        {
            return GREEN;
        }

        return values[meta];
    }

    /**
     * Reads the chroma color from a color-carrying block-item stack. Colors are
     * carried in the vanilla {@code BlockStateTag} NBT ({@code {color: "red"}}),
     * which vanilla honors during placement. Missing/unknown tag → {@link
     * #GREEN} (the legacy default meta 0), matching the meta clamp.
     */
    public static ChromaColor fromStack(ItemStack stack)
    {
        if (stack == null || !stack.hasNbt())
        {
            return GREEN;
        }

        NbtCompound tag = stack.getSubNbt(BlockItem.BLOCK_STATE_TAG_KEY);

        if (tag == null || !tag.contains("color", NbtElement.STRING_TYPE))
        {
            return GREEN;
        }

        return byName(tag.getString("color"));
    }

    /**
     * Lookup by serialized name, defaulting to {@link #GREEN} (total reader).
     */
    public static ChromaColor byName(String name)
    {
        for (ChromaColor color : values())
        {
            if (color.name.equals(name))
            {
                return color;
            }
        }

        return GREEN;
    }
}
