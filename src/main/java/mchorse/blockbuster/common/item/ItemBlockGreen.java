package mchorse.blockbuster.common.item;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;

/**
 * P97: block item for the chroma (green screen) blocks.
 *
 * <p>Legacy {@code ItemBlockGreen extends ItemColored} (metadata-subtyped with
 * per-color tint). On 1.20.4 there is no item metadata: the 8 colors live as
 * blockstates and ride the vanilla {@code BlockStateTag} NBT instead
 * ({@code BlockGreen.colorStack}). This subclass keeps the legacy class name for
 * diff-ability; all color behavior (pick, tooltip, placement) lives on
 * {@link mchorse.blockbuster.common.block.BlockGreen}.</p>
 */
public class ItemBlockGreen extends BlockItem
{
    public ItemBlockGreen(Block block, Settings settings)
    {
        super(block, settings);
    }
}
