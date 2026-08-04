package mchorse.metamorph.client.render;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.metamorph.entity.EntityMorph;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Morph ghost renderer (roadmap P56.1).
 *
 * <p>Port of Metamorph 1.4's {@code RenderMorph}: the ghost is its morph, drawn
 * cyan and translucent, bobbing and spinning, fading in over the 30-tick spawn
 * grace. The three numbers that make it read as a pickup rather than a mob —
 * the {@code (0.1, 0.9, 1.0)} tint, the {@code 0.7} alpha ceiling and the
 * {@code 5°}/tick spin — are legacy's and are kept verbatim, as is the trick of
 * driving <b>scale from alpha</b> so the ghost grows into place as it fades
 * in.</p>
 *
 * <p><b>1.20.4 mapping of the tint.</b> 1.12.2 set a global
 * {@code GlStateManager.color} and every subsequent draw picked it up. Here the
 * morph render only fills a buffer; the shader colour is sampled when that
 * buffer is <i>flushed</i>. So the tint is set, the morph is drawn, the
 * immediate consumer is flushed while the tint is still live, and only then is
 * white restored — the same ordering constraint {@code UserTextureSwap} hit
 * with texture binds.</p>
 *
 * <p><b>Not ported:</b> legacy's {@code preRenderCallback}, which is dead code
 * — it is only ever called by {@code RenderLivingBase.doRender}, and legacy
 * overrides {@code doRender} without calling super. (It is also visibly wrong:
 * it clamps three hard-coded {@code 1.0F}s.)</p>
 *
 * <p>The base class is a plain {@link EntityRenderer} rather than a living
 * renderer because there is no model to hang layers off; legacy's
 * {@code RenderLivingBase} was constructed with a {@code null} model for the
 * same reason. That also means the nameplate comes from
 * {@link EntityRenderer#render}'s own {@code hasLabel} branch, which
 * {@link #hasLabel} narrows to legacy's rule.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/render/RenderMorph.java
 */
public class RenderMorph extends EntityRenderer<EntityMorph>
{
    /** Legacy {@code GlStateManager.color(0.1F, 0.9F, 1.0F, alpha)}. */
    public static final float TINT_R = 0.1F;
    public static final float TINT_G = 0.9F;
    public static final float TINT_B = 1.0F;

    /** Legacy alpha ceiling — a ghost is never fully opaque. */
    public static final float MAX_ALPHA = 0.7F;

    /** Legacy {@code shadowSize = 0.35F}. */
    public static final float SHADOW = 0.35F;

    public RenderMorph(EntityRendererFactory.Context context)
    {
        super(context);

        this.shadowRadius = SHADOW;
    }

    @Override
    public Identifier getTexture(EntityMorph entity)
    {
        /* Legacy returns null here too — "this method isn't used". */
        return null;
    }

    /**
     * Legacy {@code canRenderName}: the ghost's name shows only while the
     * player is actually looking at it, so a field of ghosts is not a wall of
     * floating text.
     *
     * <p><b>Unreachable, on purpose.</b> A ghost is never the targeted entity —
     * {@code EntityMorph.canHit()} is false, mirroring 1.12.2, where
     * {@code canBeCollidedWith() == false} kept it out of {@code pointedEntity}
     * and this branch therefore never fired either. Ported for diff-ability
     * against the legacy renderer.</p>
     */
    @Override
    protected boolean hasLabel(EntityMorph entity)
    {
        return entity.hasCustomName() && entity == this.dispatcher.targetedEntity;
    }

    /**
     * The fade-in ramp, pure: 0 at the moment of spawn, {@link #MAX_ALPHA} once
     * the grace timer has run out, and clamped there.
     *
     * <p>Legacy wrote it as {@code 0.7 - range * 0.7} over
     * {@code range = (timer - partialTicks) / 30}, with the negative half of
     * {@code range} floored — which is why a ghost that has been sitting around
     * for a minute is at exactly 0.7 rather than drifting brighter.</p>
     */
    public static float alpha(int timer, float tickDelta)
    {
        float range = (timer - tickDelta) / (float) EntityMorph.GRACE;

        float alpha = MAX_ALPHA - (range <= 0 ? 0F : range) * MAX_ALPHA;

        return alpha > MAX_ALPHA ? MAX_ALPHA : alpha;
    }

    /** Legacy bob: {@code sin(ticks / 5) * 0.1 + 0.2}. */
    public static float bob(float ticks)
    {
        return (float) (Math.sin(ticks / 5.0F) * 0.1F + 0.2F);
    }

    @Override
    public void render(EntityMorph entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light)
    {
        if (entity.morph == null)
        {
            return;
        }

        float alpha = alpha(entity.timer, tickDelta);
        float ticks = entity.age + tickDelta;

        matrices.push();

        matrices.translate(0, bob(ticks), 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ticks * 5.0F));
        matrices.scale(alpha, alpha, alpha);

        RenderSystem.setShaderColor(TINT_R, TINT_G, TINT_B, alpha);

        try
        {
            MorphRenderPipeline.drawEntity(entity.morph, entity, matrices, consumers, light, tickDelta);

            /* The tint is read at flush time, not at draw time — flush while it
             * is still set (see the class note). */
            if (consumers instanceof VertexConsumerProvider.Immediate immediate)
            {
                immediate.draw();
            }
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            matrices.pop();
        }

        /* The label pass (EntityRenderer.render's own body). */
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }
}
