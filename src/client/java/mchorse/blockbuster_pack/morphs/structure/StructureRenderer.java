package mchorse.blockbuster_pack.morphs.structure;

import java.util.LinkedHashMap;
import java.util.Map;

import mchorse.blockbuster_pack.morphs.StructureMorph;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

/**
 * Client-side baked structure renderer (roadmap P162) — the 1.20.4 equivalent
 * of 2.7.2's {@code StructureRenderer}.
 *
 * <p>Holds the decoded {@link StructureData} and its {@link FakeBlockRenderView}
 * plus the {@link StructureStatus} load-state machine. Geometry is drawn through
 * {@code BlockRenderManager} into the render-context {@link VertexConsumerProvider}
 * (the modern replacement for the legacy retained {@code int[]} vertex buffers
 * with the {@code TEX_2S->PADDING} world-lighting swap — that vertex-format trick
 * has no core-profile analogue; see the S14 parity note / open question #2). The
 * per-block bake is re-emitted each frame, anchored at the bounding box's
 * <b>bottom center</b>: {@link StructureData} keys blocks by their raw
 * template-local positions ({@code 0..size-1}, no paste offset — unlike legacy's
 * fake world, which pasted at {@code (1,1,1)}), so the {@code -w/2, 0, -d/2}
 * translation puts the box's feet on the local origin — standing on the entity
 * position in world, and on the {@code (x, y)} anchor in the picker.</p>
 *
 * <p>The {@link #renderTimes} guard is the same static, cross-renderer
 * structure-in-structure recursion cap (legacy used the GL modelview-stack depth;
 * the core profile has no such stack, so a plain depth counter reproduces the
 * effect).</p>
 */
@Environment(EnvType.CLIENT)
public class StructureRenderer
{
    /** Static, cross-renderer recursion guard for structure-in-structure TEs. */
    public static int renderTimes = 0;

    public StructureStatus status = StructureStatus.UNLOADED;
    public StructureData data;
    public FakeBlockRenderView world;
    public BlockPos size;

    /** Lazily instantiated block entities for TE rendering, keyed by position. */
    private final Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<BlockPos, BlockEntity>();

    private final Random random = Random.create();

    public StructureRenderer()
    {}

    public StructureRenderer(StructureData data)
    {
        this.data = data;
        Vec3i s = data.size;
        this.size = new BlockPos(s.getX(), s.getY(), s.getZ());
        this.world = new FakeBlockRenderView(data);
        this.status = StructureStatus.LOADED;
    }

    /**
     * Draw the baked block geometry. The caller supplies the render-context
     * matrices/consumers and the packed light/overlay; the light and the morph's
     * biome are threaded into the fake view, which the per-face bake then
     * samples (see {@link FakeBlockRenderView}'s lighting notes).
     */
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, StructureMorph morph, int light, int overlay)
    {
        if (this.status != StructureStatus.LOADED || this.data == null)
        {
            return;
        }

        this.world.setLight(light);
        this.world.biome = resolveBiome(morph);

        BlockRenderManager dispatcher = MinecraftClient.getInstance().getBlockRenderManager();

        int w = this.size.getX();
        int d = this.size.getZ();

        matrices.push();
        /* Bottom-center anchor: blocks span [0, w]×[0, h]×[0, d] (template-local
         * 0-based positions), so this centers X/Z and leaves the box standing
         * on the local origin. */
        matrices.translate(-w / 2.0F, 0.0F, -d / 2.0F);

        for (Map.Entry<BlockPos, BlockState> entry : this.data.blocks.entrySet())
        {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            FluidState fluid = state.getFluidState();

            if (!fluid.isEmpty())
            {
                this.renderFluid(dispatcher, matrices, consumers, pos, state, fluid);
            }

            if (state.getRenderType() == BlockRenderType.INVISIBLE)
            {
                continue;
            }

            RenderLayer layer = RenderLayers.getBlockLayer(state);
            VertexConsumer consumer = consumers.getBuffer(layer);

            matrices.push();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            dispatcher.renderBlock(state, pos, this.world, matrices, consumer, true, this.random);
            matrices.pop();
        }

        matrices.pop();
    }

    /**
     * Emit one position's fluid (a fluid block, or the water half of a
     * waterlogged state). {@code FluidRenderer} writes vertices at
     * chunk-section-local coordinates ({@code pos & 15}) with no matrix
     * transform — a chunk rebuild adds the section origin at draw time — so the
     * section origin is translated back in here and the buffer is wrapped in a
     * {@link MatrixVertexConsumer} that applies the pose per vertex. Side
     * culling, height joins, biome water tint and the threaded light all come
     * from the fake view, same as the block bake.
     */
    private void renderFluid(BlockRenderManager dispatcher, MatrixStack matrices, VertexConsumerProvider consumers, BlockPos pos, BlockState state, FluidState fluid)
    {
        RenderLayer layer = RenderLayers.getFluidLayer(fluid);

        matrices.push();
        matrices.translate(pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);

        VertexConsumer consumer = new MatrixVertexConsumer(consumers.getBuffer(layer), matrices.peek());

        dispatcher.renderFluid(pos, this.world, consumer, state, fluid);
        matrices.pop();
    }

    /**
     * The morph's render biome, resolved against the client world's dynamic
     * registry (the same lookup the editor's biome list uses). An unknown id
     * falls back to the default ({@code minecraft:ocean}), and a missing client
     * world (headless) yields null — untinted, never an error.
     */
    private static Biome resolveBiome(StructureMorph morph)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.world == null)
        {
            return null;
        }

        Registry<Biome> registry = mc.world.getRegistryManager().get(RegistryKeys.BIOME);
        Biome biome = registry.get(morph.getBiome());

        return biome == null ? registry.get(StructureMorph.DEFAULT_BIOME) : biome;
    }

    /**
     * Render the structure's block entities, guarded by the static recursion
     * cap. Total by contract — a TE that cannot render on the fake view is
     * skipped, never crashes.
     *
     * <p>Each TE's renderer is invoked <b>directly</b> with the caller's packed
     * light/overlay — the public {@code BlockEntityRenderDispatcher.render}
     * entry point silently drops any block entity whose {@code hasWorld()} is
     * false (and distance-culls by the structure-local position).</p>
     *
     * <p>The client world is attached to every TE: the vanilla renderers treat
     * a world-less block entity as "rendering as an item" and draw a hardcoded
     * default orientation, ignoring the cached state entirely (banners stand
     * upright on a pillar, chests face south, shulker boxes point up, beds show
     * the head half). With a world attached they read the cached state — the
     * structure's actual wall-banner facing, chest rotation, and so on. These
     * TEs are never ticked or registered anywhere, so the reference only
     * steers rendering (and feeds {@code world.getTime()} to sway/animation
     * seeds); it is re-checked each frame in case the player switched
     * dimensions.</p>
     */
    public void renderTEs(MatrixStack matrices, VertexConsumerProvider consumers, StructureMorph morph, float tickDelta, int light, int overlay)
    {
        if (renderTimes >= 10 || this.data == null || this.data.blockEntities.isEmpty())
        {
            return;
        }

        renderTimes++;

        try
        {
            int w = this.size.getX();
            int d = this.size.getZ();

            MinecraftClient mc = MinecraftClient.getInstance();
            BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();

            for (Map.Entry<BlockPos, NbtCompound> entry : this.data.blockEntities.entrySet())
            {
                BlockPos pos = entry.getKey();
                BlockEntity te = this.blockEntities.get(pos);

                if (te == null)
                {
                    BlockState state = this.data.blocks.get(pos);

                    if (state == null)
                    {
                        continue;
                    }

                    te = BlockEntity.createFromNbt(pos, state, entry.getValue());

                    if (te == null)
                    {
                        continue;
                    }

                    this.blockEntities.put(pos, te);
                }

                BlockEntityRenderer<BlockEntity> renderer = dispatcher.get(te);

                if (renderer == null)
                {
                    continue;
                }

                if (te.getWorld() != mc.world)
                {
                    te.setWorld(mc.world);
                }

                matrices.push();
                /* Same bottom-center anchor as render()'s block loop. */
                matrices.translate(pos.getX() - w / 2.0, pos.getY(), pos.getZ() - d / 2.0);

                try
                {
                    renderer.render(te, tickDelta, matrices, consumers, light, overlay);
                }
                catch (Exception ignored)
                {
                    /* Beacon/gateway and world-dependent TE renderers may not
                     * survive the fake view — skip, never crash (total). */
                }

                matrices.pop();
            }
        }
        finally
        {
            renderTimes--;
        }
    }

    public void delete()
    {
        if (this.data != null)
        {
            this.data = null;
            this.world = null;
            this.blockEntities.clear();
            this.status = StructureStatus.UNLOADED;
        }
    }
}
