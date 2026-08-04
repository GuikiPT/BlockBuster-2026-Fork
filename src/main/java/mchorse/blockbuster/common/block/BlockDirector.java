package mchorse.blockbuster.common.block;

import mchorse.blockbuster.common.tileentity.TileEntityDirector;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.blockbuster.utils.IClientLanguage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Director block (roadmap P94 / P129).
 *
 * <p>Port of 1.12.2 {@code common/block/BlockDirector.java}. This block is a
 * <b>deprecated relic</b> in 2.7.2 — it no longer directs anything. Its only
 * live behaviors are: the {@link #PLAYING}/{@link #HIDDEN} state pair, the
 * constant west/east redstone emission keyed on {@code PLAYING}, the hidden
 * ghost mode (no collision, BE-rendered, non-opaque), unbreakability, and the
 * farewell chat message on right-click that links players to a migration video
 * (Bilibili for Chinese-language clients, YouTube otherwise). The
 * {@link TileEntityDirector} it provides performs the old-world
 * {@code Actors}-NBT → {@code scenes/director_block_x_y_z.dat} migration.</p>
 *
 * <h2>Load-bearing legacy quirks</h2>
 * <ul>
 *   <li><b>Redstone truth table</b> — emits 15 on exactly one of WEST/EAST at
 *       all times: WEST while {@code PLAYING}, EAST while not. Nothing in 2.7.2
 *       ever sets {@code PLAYING}, so in practice the block constantly powers its
 *       EAST face; reproduced verbatim (do <i>not</i> "optimize" to only-while-
 *       playing). The yarn {@code getWeakRedstonePower} direction uses the same
 *       call convention as 1.12's {@code getWeakPower} side (neighbor =
 *       consumer.offset(dir), queried with dir), so the WEST/EAST values map 1:1
 *       with no code-level inversion.</li>
 *   <li><b>Meta bit-0 inversion</b> — legacy {@code getMetaFromState} wrote
 *       {@code PLAYING ? 0 : 1} but {@code getStateFromMeta} read
 *       {@code (meta & 1) == 1} as playing. That inconsistency only matters for
 *       S20 world-blockstate migration (flagged in the P71/S20 notes); the
 *       Fabric block stores properties directly, so it cannot reproduce the bug
 *       and does not need to.</li>
 *   <li><b>No comparator output</b> — verified against legacy; do not add
 *       {@code hasComparatorOutput}.</li>
 * </ul>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/common/block/BlockDirector.java}.</p>
 */
public class BlockDirector extends Block implements BlockEntityProvider
{
    /** The playing-state property of the director block. */
    public static final BooleanProperty PLAYING = BooleanProperty.of("playing");

    /** The hidden-state property of the director block. */
    public static final BooleanProperty HIDDEN = BooleanProperty.of("hidden");

    public static final String YOUTUBE_URL = "https://youtu.be/nMOb8RnuyuE";
    public static final String BILIBILI_URL = "https://bilibili.com/video/BV1SV41117Pm";

    public BlockDirector(Settings settings)
    {
        super(settings);

        this.setDefaultState(this.getStateManager().getDefaultState().with(PLAYING, false).with(HIDDEN, false));
    }

    /* Legacy lang-key parity: the converted lang JSONs keep 1.12.2's keys 1:1,
     * so point the translation key at the legacy id (tile.blockbuster.director.name). */
    @Override
    public String getTranslationKey()
    {
        return "tile.blockbuster.director.name";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(PLAYING, HIDDEN);
    }

    /* Hidden ghost mode */

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        /* Legacy: ENTITYBLOCK_ANIMATED when hidden (the BE renderer draws the
         * F3 debug cube) else the normal baked model. INVISIBLE is the 1.20.4
         * equivalent of "no baked chunk model, BE-rendered". */
        return state.get(HIDDEN) ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos)
    {
        /* Legacy isOpaqueCube/isFullCube = !HIDDEN. A hidden director must not
         * cull neighbouring faces. */
        return state.get(HIDDEN) ? VoxelShapes.empty() : VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        /* Legacy getCollisionBoundingBox returned null when hidden. */
        return state.get(HIDDEN) ? VoxelShapes.empty() : VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        /* Legacy only nulled the collision box — the block stays targetable when
         * hidden. */
        return VoxelShapes.fullCube();
    }

    /* Redstone */

    @Override
    public boolean emitsRedstonePower(BlockState state)
    {
        return true;
    }

    /**
     * Power the WEST side while the block is playing and the EAST side while it
     * is not (legacy {@code getWeakPower}). Emits 15 on exactly one of the two
     * at all times.
     */
    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction)
    {
        return weakPower(state.get(PLAYING), direction);
    }

    /**
     * Pure redstone truth table, extracted so it can be unit-tested without a
     * world. Mirrors the legacy expression verbatim.
     */
    public static int weakPower(boolean playing, Direction direction)
    {
        return (playing && direction == Direction.WEST) || (!playing && direction == Direction.EAST) ? 15 : 0;
    }

    /* Player interaction — the farewell message */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer)
        {
            EntityUtils.sendStatusMessage(serverPlayer, Text.translatable("blockbuster.bye_director_block"));

            MutableText link = Text.translatable("blockbuster.bye_director_block_guide");
            MutableText youtube = Text.translatable("blockbuster.bye_director_block_guide_watch");
            String url = getUrl(serverPlayer);

            youtube.setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(url)))
                .withColor(Formatting.GRAY)
                .withUnderline(true));

            serverPlayer.sendMessage(link.append(youtube).append(Text.literal(".")));
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Get the migration-video URL based on the player's client language: the
     * Bilibili link for Chinese-language clients ({@code zh_*}), YouTube
     * otherwise.
     *
     * <p>Legacy obtained the language by reflecting the first {@code String}
     * field of {@code EntityPlayerMP}. 1.20.1 keeps no language field on
     * {@code ServerPlayerEntity} at all, so the port re-adds one via
     * {@code ServerPlayerEntityLanguageMixin} and reads it through
     * {@link IClientLanguage}. Behavioral output is identical.</p>
     */
    public static String getUrl(ServerPlayerEntity player)
    {
        String language = ((IClientLanguage) player).blockbuster$getClientLanguage();

        return chinese(language) ? BILIBILI_URL : YOUTUBE_URL;
    }

    /** The pure language → is-Chinese test, exposed for headless message tests. */
    public static boolean chinese(String language)
    {
        return language != null && language.startsWith("zh_");
    }

    /* Block entity */

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new TileEntityDirector(pos, state);
    }
}
