package mchorse.blockbuster.common.item;

import java.util.List;

import mchorse.blockbuster.common.block.BlockModel;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * Model block light variant item (P95 step 8 / P98, id {@code blockbuster:model1}
 * … {@code model15}).
 *
 * <p>Port of 1.12.2 {@code common/item/ItemBlockModel}. The legacy class is a
 * plain {@code Item} and <b>not</b> an {@code ItemBlock} on purpose — its own
 * javadoc gives both reasons (Optifine's {@code dynamic_lights.properties}
 * ignores {@code ItemBlock} subclasses, and Spigot servers drop NBT for block
 * items that are not {@code ItemBlock}s). The port keeps it a plain
 * {@link Item} for a third, 1.20.4-specific reason as well: {@code BlockItem}
 * registers itself into {@code Item.BLOCK_ITEMS}, so 15 extra
 * {@code BlockItem}s for the same block would hijack
 * {@code Blockbuster.MODEL_BLOCK.asItem()} away from
 * {@code blockbuster:model}.</p>
 *
 * <p>Only light 0 is a real {@code BlockItem} ({@code blockbuster:model},
 * registered in {@code Blockbuster.registerModelBlock}); lights 1–15 are these
 * items, whose {@link #useOnBlock} body mirrors vanilla {@code BlockItem.place}
 * while forcing {@link BlockModel#LIGHT} to {@link #lightValue} — exactly what
 * legacy's copied {@code ItemBlock.onItemUse} did by passing
 * {@code this.lightValue} as the placement metadata.</p>
 *
 * <p>Legacy placement used {@code world.setBlockState(pos, state, 11)}; on
 * 1.20.4 the literal 11 is {@link Block#NOTIFY_ALL_AND_REDRAW}
 * ({@code NOTIFY_NEIGHBORS | NOTIFY_LISTENERS | REDRAW_ON_MAIN_THREAD} = 1|2|8,
 * verified against the named jar), which is also what vanilla {@code BlockItem}
 * uses — so the flag maps 1:1.</p>
 *
 * <p>Max stack size is the vanilla default 64 (legacy never called
 * {@code setMaxStackSize} on these) — that is why
 * {@link BlockModel#setTENBTtoStack} skipping the default {@code BlockEntityTag}
 * matters: default model-block items must stay stackable.</p>
 */
public class ItemBlockModel extends Item
{
    private final BlockModel block;
    private final int lightValue;
    private final String translationKey;

    public ItemBlockModel(BlockModel block, int lightValue)
    {
        super(new Item.Settings());

        this.block = block;
        this.lightValue = lightValue;

        /* Legacy setUnlocalizedName("blockbuster.model" + (light != 0 ? light : ""))
         * → item.blockbuster.model<N>.name; the converted lang JSONs keep those
         * keys 1:1 (LangConversionTest). */
        this.translationKey = "item.blockbuster.model" + (lightValue != 0 ? String.valueOf(lightValue) : "") + ".name";
    }

    public BlockModel getBlock()
    {
        return this.block;
    }

    public int getLightValue()
    {
        return this.lightValue;
    }

    @Override
    public String getTranslationKey()
    {
        return this.translationKey;
    }

    @Override
    public String getTranslationKey(ItemStack stack)
    {
        return this.translationKey;
    }

    /**
     * Legacy {@code addInformation} delegated to {@code block.addInformation}
     * ({@code blockbuster.info.model_block}).
     */
    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context)
    {
        this.block.appendTooltip(stack, world, tooltip, context);
    }

    /**
     * The model block carried its light in the block metadata; the item supplies
     * it (legacy passed {@code int i = this.lightValue} as the placement meta to
     * {@code getStateForPlacement}). Extracted so it can be unit-tested without
     * a world.
     */
    public BlockState withLight(BlockState state)
    {
        return state.with(BlockModel.LIGHT, this.lightValue);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        return this.place(new ItemPlacementContext(context));
    }

    /**
     * Vanilla {@code BlockItem.place} with the legacy light override folded in.
     * Kept as its own method so the headless placement test can drive it.
     */
    public ActionResult place(ItemPlacementContext context)
    {
        if (!context.canPlace())
        {
            return ActionResult.FAIL;
        }

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();

        /* Legacy: !itemstack.isEmpty() && player.canPlayerEdit(pos, facing, itemstack) */
        if (stack.isEmpty() || (player != null && !player.canPlaceOn(pos, context.getSide(), stack)))
        {
            return ActionResult.FAIL;
        }

        BlockState placement = this.block.getPlacementState(context);

        if (placement == null)
        {
            return ActionResult.FAIL;
        }

        BlockState state = this.withLight(placement);

        /* Legacy: world.mayPlace(this.block, pos, false, facing, null) */
        if (!world.canPlace(state, pos, ShapeContext.absent()))
        {
            return ActionResult.FAIL;
        }

        if (!world.setBlockState(pos, state, Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD))
        {
            return ActionResult.FAIL;
        }

        BlockState placed = world.getBlockState(pos);

        if (placed.isOf(this.block))
        {
            /* Legacy's copied setTileEntityNBT: applies the stack's
             * BlockEntityTag, keeping the ops-only gate
             * (copyItemDataRequiresOperator). Vanilla's static does exactly
             * that, so no second copy of the body is needed. */
            BlockItem.writeNbtToBlockEntity(world, player, pos, stack);

            this.block.onPlaced(world, pos, placed, player, stack);

            if (player instanceof ServerPlayerEntity serverPlayer)
            {
                Criteria.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
            }
        }

        BlockSoundGroup sounds = placed.getSoundGroup();

        world.playSound(player, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS,
            (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
        world.emitGameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Emitter.of(player, placed));

        if (player == null || !player.getAbilities().creativeMode)
        {
            stack.decrement(1);
        }

        return ActionResult.success(world.isClient);
    }
}
