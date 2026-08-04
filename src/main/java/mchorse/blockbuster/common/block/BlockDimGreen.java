package mchorse.blockbuster.common.block;

import java.util.List;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.BlockView;

/**
 * P97: dim chroma block — a chroma block that "doesn't glow".
 *
 * <p>Faithful port of {@code BlockDimGreen} (extends {@link BlockGreen} with
 * {@code setLightLevel(0)}). Registered with {@code luminance(0)} so it emits no
 * light. Unlike {@code green}, it is <b>not</b> rendered full-bright: the
 * {@code WorldRendererLightmapMixin} skips it, so vanilla's lightmap path runs
 * and reproduces the legacy {@code getPackedLightmapCoords} value (the real
 * combined light). The legacy slab-below fallback is a documented no-op — see
 * {@link ChromaLight}.</p>
 */
public class BlockDimGreen extends BlockGreen
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "tile.blockbuster.dim_green.name";
    }

    public BlockDimGreen(Settings settings)
    {
        super(settings);
    }

    /**
     * Legacy {@code addInformation}: the green-block tooltip plus the extra
     * {@code blockbuster.info.dim_green_block} line.
     */
    @Override
    public void appendTooltip(ItemStack stack, BlockView world, List<Text> tooltip, TooltipContext options)
    {
        super.appendTooltip(stack, world, tooltip, options);

        tooltip.add(Text.translatable("blockbuster.info.dim_green_block"));
    }
}
