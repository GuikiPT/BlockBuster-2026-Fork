package mchorse.blockbuster.common.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/**
 * P97: chroma (green screen) block — 8 colors, blast-proof, rendered
 * full-bright for uniform chroma keying.
 *
 * <p>Faithful port of {@code mchorse.blockbuster.common.block.BlockGreen}
 * (Blockbuster 2.7.2). Legacy kept <b>one block id</b> ({@code blockbuster:green})
 * with the color as blockstate metadata — the port keeps that shape as an
 * {@link EnumProperty} {@code color} so 1.12 worlds migrate 1:1 (S20 maps
 * {@code meta -> color} by ordinal, clamp-to-GREEN via
 * {@link ChromaColor#byMeta(int)}). BBS's eight-separate-blocks flattening is
 * <b>not</b> copied — it would change the block id.</p>
 *
 * <ul>
 *   <li>Blast resistance 6000000 (indestructible by explosions) but hardness
 *       stays default (breakable, unlike the director).</li>
 *   <li>{@code luminance(1)} — emits light level <b>1</b> into the world (set
 *       at registration), exactly like 1.12's {@code setLightLevel(1/15)}.
 *       Full-bright <i>rendering</i> is a separate lightmap override
 *       ({@link ChromaLight} / {@code WorldRendererLightmapMixin}), never
 *       {@code luminance(15)} which would leak light and break film lighting.</li>
 *   <li>{@link #getAmbientOcclusionLightLevel} forced to {@code 1.0} so chroma
 *       corners don't render with AO seams.</li>
 * </ul>
 */
public class BlockGreen extends Block
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "tile.blockbuster.green.name";
    }

    public static final EnumProperty<ChromaColor> COLOR = EnumProperty.of("color", ChromaColor.class);

    /**
     * Shared chroma block settings (legacy {@code Material.CLAY} + blast
     * resistance 6000000, hardness default 0 so it stays breakable). Luminance
     * is applied per-variant by the caller: 1 for {@code green}, 0 for
     * {@code dim_green}.
     */
    public static Settings chromaSettings()
    {
        return Settings.create()
            .mapColor(MapColor.PALE_GREEN)
            .strength(0.0F, 6000000.0F)
            .sounds(BlockSoundGroup.STONE);
    }

    public BlockGreen(Settings settings)
    {
        super(settings);

        this.setDefaultState(this.getStateManager().getDefaultState().with(COLOR, ChromaColor.GREEN));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(COLOR);
    }

    /**
     * Legacy {@code getPickBlock}: pick returns a stack carrying the picked
     * color. On 1.20.4 the color rides the vanilla {@code BlockStateTag} NBT
     * ({@code {color: "<name>"}}) which the block item honors on placement.
     */
    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        return colorStack(this, state.get(COLOR));
    }

    /**
     * Builds a color-carrying block-item stack ({@code {BlockStateTag:{color}}}).
     * Used by pick-block and by the creative tab (P99) to emit one stack per
     * color, replacing the legacy metadata sub-items.
     */
    public static ItemStack colorStack(Block block, ChromaColor color)
    {
        ItemStack stack = new ItemStack(block);

        if (color != ChromaColor.GREEN)
        {
            NbtCompound tag = new NbtCompound();

            tag.putString("color", color.asString());
            stack.setSubNbt(BlockItem.BLOCK_STATE_TAG_KEY, tag);
        }

        return stack;
    }

    /**
     * Legacy {@code addInformation}: {@code blockbuster.info.green_block}
     * formatted with the color name {@code blockbuster.chroma_blocks.<ordinal>}.
     */
    @Override
    public void appendTooltip(ItemStack stack, BlockView world, List<Text> tooltip, TooltipContext options)
    {
        ChromaColor color = ChromaColor.fromStack(stack);

        tooltip.add(Text.translatable("blockbuster.info.green_block", Text.translatable("blockbuster.chroma_blocks." + color.ordinal())));
    }

    /**
     * Legacy {@code getAmbientOcclusionLightValue}: force AO light to 1.0 (no
     * darkening) so chroma faces are seam-free.
     */
    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos)
    {
        return 1.0F;
    }
}
