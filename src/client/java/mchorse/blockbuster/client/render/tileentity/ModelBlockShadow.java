package mchorse.blockbuster.client.render.tileentity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * The model block's drop shadow (roadmap P229).
 *
 * <p>Legacy {@code TileEntityModelRenderer.RenderShadow} was a bare
 * {@code Render<Entity>} subclass whose only purpose was to reach the protected
 * {@code doRenderShadowAndFire} — so the model block's shadow was literally the
 * vanilla entity shadow, sized by the morph and anchored at the morph's offset
 * position. This class is that call, in 1.20.4 terms.</p>
 *
 * <h2>The legacy gate, preserved</h2>
 * {@code Render.doRenderShadowAndFire} drew nothing unless <b>all</b> of:
 * {@code options.entityShadows} on, {@code shadowSize > 0}, the entity not
 * invisible, and {@code renderManager.isRenderShadow()} — then faded the shadow
 * with {@code (1 - distanceSq / 256) * shadowOpaque} and skipped the draw when
 * that reached zero. All five survive here verbatim; the yarn spellings are
 * {@link GameOptions#getEntityShadows()}, {@code EntityRenderDispatcher.renderShadows}
 * and {@link EntityRenderDispatcher#getSquaredDistanceToCamera(double, double, double)}.
 *
 * <p>The fire half of {@code doRenderShadowAndFire} is deliberately dropped: the
 * model block's host is a never-ticked dummy actor that cannot be on fire, and
 * legacy's own {@code canRenderOnFire()} branch was therefore always false.</p>
 *
 * <h2>Where the shadow lands</h2>
 * Vanilla's {@code renderShadow} draws at the <b>matrix origin</b> and samples
 * the ground from the entity's own (lerped) world position. The block-entity
 * matrix arrives translated to the block corner, so this pushes the settings
 * offset — the same {@code xx/yy/zz} legacy passed to
 * {@code doRenderShadowAndFire} — and the renderer has already placed the dummy
 * entity at {@code pos + offset}, so both halves agree. Note the caller draws
 * the shadow <b>after</b> popping the rotation/scale transform: a rotated model
 * block still gets an upright shadow, which is a legacy quirk, not an oversight.
 *
 * Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/tileentity/TileEntityModelRenderer.java}
 * (the {@code teSettings.isShadow()} branch) + vanilla 1.12.2
 * {@code Render.doRenderShadowAndFire}.
 */
public final class ModelBlockShadow
{
    /** Legacy's fade denominator: the shadow is gone 16 blocks from the camera. */
    public static final double FADE_DISTANCE_SQ = 256.0D;

    private ModelBlockShadow()
    {}

    /**
     * The {@link TileEntityModelRenderer.ShadowDrawer} implementation installed
     * by {@link ModelBlockRenderWiring}.
     */
    public static void draw(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, double x, double y, double z, float shadowSize, float shadowOpacity, float tickDelta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.world == null || matrices == null || vertexConsumers == null)
        {
            return;
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        if (!shouldDraw(mc.options, dispatcher, entity, shadowSize))
        {
            return;
        }

        float alpha = opacity(dispatcher.getSquaredDistanceToCamera(entity.getX(), entity.getY(), entity.getZ()), shadowOpacity);

        if (alpha <= 0F)
        {
            return;
        }

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            EntityRenderDispatcher.renderShadow(matrices, vertexConsumers, entity, alpha, tickDelta, mc.world, shadowSize);
        }
        finally
        {
            matrices.pop();
        }
    }

    /**
     * Legacy {@code doRenderShadowAndFire}'s four boolean preconditions, split
     * out so they are unit-testable without a client.
     */
    public static boolean shouldDraw(GameOptions options, EntityRenderDispatcher dispatcher, LivingEntity entity, float shadowSize)
    {
        return options != null
            && dispatcher != null
            && entity != null
            && shadowSize > 0F
            && options.getEntityShadows().getValue()
            && dispatcher.renderShadows
            && !entity.isInvisible();
    }

    /**
     * Legacy's distance fade: {@code (1 - distanceSq / 256) * shadowOpaque}.
     * Negative results are the caller's cue to skip the draw entirely (legacy
     * tested {@code f > 0}).
     */
    public static float opacity(double squaredDistanceToCamera, float shadowOpacity)
    {
        return (float) ((1.0D - squaredDistanceToCamera / FADE_DISTANCE_SQ) * shadowOpacity);
    }
}
