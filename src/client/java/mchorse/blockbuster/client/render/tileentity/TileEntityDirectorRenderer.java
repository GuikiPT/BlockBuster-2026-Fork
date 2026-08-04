package mchorse.blockbuster.client.render.tileentity;

import mchorse.blockbuster.common.tileentity.TileEntityDirector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Director block-entity renderer (roadmap P94 / P129).
 *
 * <p>Port of 1.12.2 {@code client/render/tileentity/TileEntityDirectorRenderer.java}.
 * It draws nothing but a half-size translucent red debug cube, and only while
 * the F3 debug HUD is showing and the GUI is not hidden — a locator overlay so
 * builders can still find (invisible/hidden) director blocks left over in old
 * worlds.</p>
 *
 * <p>The legacy renderer manually disabled any bound GL shader program (an
 * Optifine-compat hack), depth/lighting/texture, and enabled blend around a
 * {@code Draw.cube}. Under the core profile that raw-GL dance is obsolete; the
 * modern equivalent renders the box through the vanilla
 * {@link RenderLayer#getDebugFilledBox()} layer (blend + no-texture baked in),
 * so no {@code RenderSystem} state is touched here — an accepted mechanical
 * difference documented in the S6 technique ledger.</p>
 *
 * <p>Registered from {@code BlockbusterClient} via
 * {@code BlockEntityRendererFactories.register}. Excluded from headless tests
 * (client source set); verified by source review.</p>
 */
public class TileEntityDirectorRenderer implements BlockEntityRenderer<TileEntityDirector>
{
    public TileEntityDirectorRenderer(BlockEntityRendererFactory.Context ctx)
    {}

    @Override
    public void render(TileEntityDirector entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || !mc.getDebugHud().shouldShowDebugHud() || mc.options.hudHidden)
        {
            return;
        }

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());

        /* Legacy Draw.cube(x + 0.25 .. x + 0.75, red, alpha 0.5): a half-size,
         * block-centered translucent red cube. */
        WorldRenderer.renderFilledBox(matrices, buffer, 0.25D, 0.25D, 0.25D, 0.75D, 0.75D, 0.75D, 1.0F, 0.0F, 0.0F, 0.5F);
    }

    @Override
    public boolean rendersOutsideBoundingBox(TileEntityDirector entity)
    {
        /* Legacy getRenderBoundingBox = INFINITE_EXTENT_AABB (always rendered). */
        return true;
    }

    @Override
    public int getRenderDistance()
    {
        /* Legacy getMaxRenderDistanceSquared = actorRenderingRange² (config
         * actor_rendering_range, default 256). SEAM(P208): the config option is
         * not registered yet — use the legacy default until it lands. */
        return 256;
    }
}
