package mchorse.blockbuster.common.block;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.GuiHandler;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.FluidFillable;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import org.jetbrains.annotations.Nullable;

/**
 * Model block (roadmap P95).
 *
 * <p>Port of 1.12.2 {@code common/block/BlockModel.java}. The block that
 * provides a {@link TileEntityModel} for rendering a morph. The legacy 16
 * metadata variants become 16 <b>state</b> variants of the single
 * {@code blockbuster:model} block via {@link #LIGHT} (0..15 = actual
 * luminance), plus 16 <b>item</b> ids registered separately: legacy
 * {@code modelBlockItems[0]} is a plain {@code ItemBlock} sharing the block's id
 * ({@code blockbuster:model}) and {@code modelBlockItems[1..15]} are plain
 * {@code Item}s ({@link mchorse.blockbuster.common.item.ItemBlockModel} —
 * Optifine dynamic-lights + Spigot NBT reasons). All 16 are registered in
 * {@code Blockbuster.registerModelBlock}.</p>
 *
 * <p>Preserved quirks: light value == block luminance; no fence connection;
 * not a full/opaque cube; mobs may spawn inside; collision box present only
 * when the BE's hitbox toggle is on; sneak-place initial yaw; pick-block builds
 * a <b>fresh default</b> TE at the state's light (legacy {@code getItem}), while
 * breaking preserves the placed BE's full NBT (legacy {@code getDrops}); the
 * {@code setTENBTtoStack} default-morph skip that keeps default items
 * stackable.</p>
 */
public class BlockModel extends Block implements BlockEntityProvider, FluidFillable
{
    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys
     * 1:1 (LangConversionTest), so point the translation key at the legacy id. */
    @Override
    public String getTranslationKey()
    {
        return "tile.blockbuster.model.name";
    }

    /**
     * Legacy {@code addInformation}: a single {@code blockbuster.info.model_block}
     * line. Both the {@code blockbuster:model} {@link net.minecraft.item.BlockItem}
     * (which delegates here automatically) and the 15
     * {@link mchorse.blockbuster.common.item.ItemBlockModel} light variants
     * (which delegate explicitly, as legacy did) show it.
     */
    @Override
    public void appendTooltip(ItemStack stack, BlockView world, List<Text> tooltip, TooltipContext options)
    {
        tooltip.add(Text.translatable("blockbuster.info.model_block"));
    }

    public static final IntProperty LIGHT = IntProperty.of("light", 0, 15);

    /**
     * Resolves the light-variant {@code Item} for a given light value into a
     * fresh stack — legacy {@code Blockbuster.modelBlockItems[meta]}. Installed
     * by {@code Blockbuster.registerModelBlock} alongside the 16 items; kept as
     * an indirection so headless tests can drive it without a registry.
     */
    public static IntFunction<ItemStack> itemStackProvider;

    public BlockModel(Settings settings)
    {
        super(settings);

        this.setDefaultState(this.getStateManager().getDefaultState().with(LIGHT, 0));
    }

    /** Legacy {@code validateTicker} idiom (BBS reference) — server ticker gate. */
    @SuppressWarnings("unchecked")
    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(LIGHT);
    }

    /**
     * Build a fresh stack for the given light value via {@link
     * #itemStackProvider} (legacy {@code getItemStack(int meta)} →
     * {@code Blockbuster.modelBlockItems[meta]}, including the out-of-range
     * clamp to index 0).
     */
    public static ItemStack getItemStack(int meta)
    {
        int light = (meta >= 0 && meta <= 15) ? meta : 0;

        if (itemStackProvider != null)
        {
            return itemStackProvider.apply(light);
        }

        /* Only reachable in tests that boot without registerContent(). */
        return ItemStack.EMPTY;
    }

    public ItemStack getItemStack(BlockState state)
    {
        return getItemStack(state.get(LIGHT));
    }

    /**
     * Write the given model TE's NBT into the stack as {@code BlockEntityTag}
     * (legacy {@code setTENBTtoStack}). {@code x}/{@code y}/{@code z} are
     * stripped, and the tag is <b>not</b> attached at all when it is exactly the
     * clean default ({@code {id, Morph}} with the default morph) — so default
     * model-block items stay stackable.
     *
     * <p>Null-safe against the not-yet-registered default morph (SEAM in
     * {@link TileEntityModel}); the comparison converges to legacy behavior once
     * {@code blockbuster.fred} resolves.</p>
     */
    public ItemStack setTENBTtoStack(ItemStack stack, TileEntityModel teModel)
    {
        NbtCompound tag = teModel.createNbtWithIdentifyingData();
        NbtCompound block = new NbtCompound();

        tag.remove("x");
        tag.remove("y");
        tag.remove("z");

        block.put("BlockEntityTag", tag);

        NbtCompound defaultMorph = TileEntityModel.getDefaultMorph() == null
            ? null : TileEntityModel.getDefaultMorph().toNBT();

        boolean cleanDefault = tag.getSize() == 2 && tag.contains("id") && tag.contains("Morph")
            && Objects.equals(tag.get("Morph"), defaultMorph);

        if (!cleanDefault)
        {
            stack.setNbt(block);
        }

        return stack;
    }

    /* Pick-block + drops */

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        ItemStack stack = getItemStack(state);

        if (stack.isEmpty())
        {
            return super.getPickStack(world, pos, state);
        }

        /* Legacy getItem: a FRESH default TE at this state's light — pick-block
         * does not carry the placed morph, only the light variant. */
        TileEntityModel model = new TileEntityModel();

        model.getSettings().setLightValue(state.get(LIGHT));
        this.setTENBTtoStack(stack, model);

        return stack;
    }

    /**
     * Legacy {@code getDrops} (via {@code ActionHandler.lastTE}): preserve the
     * <b>placed</b> BE's full NBT into the dropped stack. Fabric hands the BE to
     * {@code afterBreak} before it is removed, so no static capture is needed.
     */
    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool)
    {
        if (!world.isClient && !player.getAbilities().creativeMode)
        {
            ItemStack stack = getItemStack(state);

            if (!stack.isEmpty() && be instanceof TileEntityModel model)
            {
                this.setTENBTtoStack(stack, model);
                ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
            }
        }

        super.afterBreak(world, player, pos, state, be, tool);
    }

    /* Placement */

    /**
     * Legacy sneak-place yaw: {@code isSneaking ? wrapDegrees(180 - yaw) : 0}.
     * Extracted so it can be unit-tested without a world.
     */
    public static float placementYaw(boolean sneaking, float yaw)
    {
        return sneaking ? MathHelper.wrapDegrees(180 - yaw) : 0;
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        /* LIGHT is set by the placing item (P98); the base placement keeps the
         * default (0). */
        return this.getDefaultState();
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.onPlaced(world, pos, state, placer, stack);

        /* Legacy net effect: the TE was created with the sneak yaw, then the
         * stack's BlockEntityTag (if any) overrode the settings. Reproduce that
         * precedence by applying the sneak yaw only when the stack carries no
         * BlockEntityTag (otherwise the tag's ry already won). */
        boolean hasTag = stack.hasNbt() && stack.getNbt().contains("BlockEntityTag");

        if (placer != null && placer.isSneaking() && !hasTag)
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TileEntityModel model)
            {
                model.getSettings().setRy(placementYaw(true, placer.getYaw()));
                model.markDirty();
            }
        }
    }

    /* Interaction — open the model dashboard */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        /* Legacy onBlockActivated: client-side-direct open, gated by adventure
         * mode + the editModelBlock permission, then GuiHandler.MODEL_BLOCK.
         * The block swallows the click on both sides so it behaves like the
         * legacy block (returned true on both sides).
         *
         * P209: the adventure-mode gate + async blockbuster.model_block.edit
         * permission check live in the client-installed GuiHandler model-block
         * opener (they touch client-only singletons); the common block just
         * hands the request to it. On a dedicated server / headless the client
         * branch never runs, so no gate is needed here. */
        if (world.isClient)
        {
            GuiHandler.openModelBlock(player, pos.getX(), pos.getY(), pos.getZ());
        }

        return ActionResult.SUCCESS;
    }

    /* Block entity */

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new TileEntityModel(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return validateTicker(type, Blockbuster.MODEL_BLOCK_TILE,
            (theWorld, blockPos, blockState, blockEntity) -> ((TileEntityModel) blockEntity).update(theWorld));
    }

    /* Visual / collision properties */

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        /* Legacy ENTITYBLOCK_ANIMATED — the BE renderer (P96) draws the morph;
         * no baked chunk model. */
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        BlockEntity te = world.getBlockEntity(pos);

        if (te instanceof TileEntityModel model && model.getSettings().isBlockHitbox())
        {
            return VoxelShapes.fullCube();
        }

        return VoxelShapes.empty();
    }

    /* Fluids — the model block is neither waterloggable nor washable */

    /**
     * The model block's collision box comes from its block entity (the hitbox
     * toggle), but the per-state shape cache vanilla builds at registration time
     * resolves it against an empty block view with no BE — so the cached
     * collision shape is always empty and {@code BlockState.isSolid()} comes out
     * {@code false}. Water treats any non-solid block that it does not
     * special-case as free space: {@code FlowableFluid.canFill} falls through to
     * {@code !state.blocksMovement()}, so flowing water overwrote the block
     * (breaking and dropping it, crop-style), and {@code canBucketPlace}
     * ({@code isReplaceable() || !isSolid()}) let a bucket emptied straight onto
     * the block break it too.
     *
     * <p>Both paths consult a block-level hook first, so both are closed here
     * rather than by forcing the block solid in its settings — {@code solid()}
     * would also make mobs path around a model block that has its hitbox turned
     * off, and would feed {@code blocksMovement()} everywhere else.</p>
     *
     * <p>Returning {@code false} from {@link FluidFillable} is what makes fluids
     * refuse to enter the block at all; it does <b>not</b> add a
     * {@code waterlogged} state (that would need {@code Waterloggable}), so the
     * block stays non-waterloggable and simply displaces water when placed into
     * it, exactly as before.</p>
     */
    @Override
    public boolean canFillWithFluid(@Nullable PlayerEntity player, BlockView world, BlockPos pos, BlockState state, Fluid fluid)
    {
        return false;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState)
    {
        return false;
    }

    /** @see #canFillWithFluid — keeps a bucket from replacing the block. */
    @Override
    public boolean canBucketPlace(BlockState state, Fluid fluid)
    {
        return false;
    }
}
