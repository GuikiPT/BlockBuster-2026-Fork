package mchorse.blockbuster_pack.morphs.structure;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

/**
 * Minimal {@link BlockRenderView} backed by a decoded {@link StructureData}
 * (roadmap P162) — the 1.20.4 replacement for 2.7.2's {@code FakeWorld}.
 *
 * <p><b>Lighting.</b> The bake samples this view per face
 * ({@code BlockModelRenderer.renderQuadsFlat} &rarr;
 * {@code WorldRenderer.getLightmapCoordinates} &rarr; {@link #getLightLevel}),
 * so the world light the morph renderer computed for the entity is threaded in
 * through {@link #setLight} each frame and reported uniformly from every
 * position — the whole structure lights like the entity holding it, the
 * closest core-profile analogue to legacy's retained-buffer lighting swap
 * (S14 parity note / open question #2). Emissive blocks still win:
 * {@code getLightmapCoordinates} takes {@code max(blockLight, state.getLuminance())},
 * so glowstone inside a dark structure stays lit, exactly as in a real world.</p>
 *
 * <p><b>Face diffuse.</b> {@link #getBrightness} reports vanilla's directional
 * factors (down 0.5, up 1.0, north/south 0.8, east/west 0.6 — {@code ClientWorld}'s
 * non-darkened branch). In 1.12.2 this diffuse lived in the render pipeline and
 * never consulted the world, so the legacy bake <i>was</i> shaded; on 1.20.4 the
 * pipeline asks the view, and pinning {@code 1.0} (as this view briefly did)
 * flattened every face to the same brightness.</p>
 *
 * <p>Biome tint is resolved against the configured {@link #biome} when present
 * (grass/foliage/water colouring), otherwise blocks bake untinted.</p>
 */
@Environment(EnvType.CLIENT)
public class FakeBlockRenderView implements BlockRenderView
{
    private final StructureData data;

    /** Biome used for colour resolution; null bakes untinted. */
    public Biome biome;

    /** Light levels the next bake reports, set per frame from the caller's packed light. */
    private int blockLight = 15;
    private int skyLight = 15;

    public FakeBlockRenderView(StructureData data)
    {
        this.data = data;
    }

    /**
     * Thread the caller's packed lightmap coordinates (the entity light, or
     * fullbright when the morph's {@code lighting} flag is off) into the levels
     * {@link #getLightLevel} reports during the bake.
     */
    public void setLight(int packedLight)
    {
        this.blockLight = LightmapTextureManager.getBlockLightCoordinates(packedLight);
        this.skyLight = LightmapTextureManager.getSkyLightCoordinates(packedLight);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        BlockState state = this.data.blocks.get(pos.toImmutable());

        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        if (!shaded)
        {
            return 1.0F;
        }

        switch (direction)
        {
            case DOWN:
                return 0.5F;
            case UP:
                return 1.0F;
            case NORTH:
            case SOUTH:
                return 0.8F;
            default:
                return 0.6F;
        }
    }

    @Override
    public LightingProvider getLightingProvider()
    {
        /* Never consulted: getLightLevel is overridden directly, so the default
         * BlockRenderView delegation to the provider is bypassed on both the
         * flat and the AO bake paths. */
        return null;
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver resolver)
    {
        return this.biome != null ? resolver.getColor(this.biome, pos.getX(), pos.getZ()) : 0xFFFFFFFF;
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        return type == LightType.SKY ? this.skyLight : this.blockLight;
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
    {
        return 15;
    }

    @Override
    public int getHeight()
    {
        return 384;
    }

    @Override
    public int getBottomY()
    {
        return -64;
    }
}
