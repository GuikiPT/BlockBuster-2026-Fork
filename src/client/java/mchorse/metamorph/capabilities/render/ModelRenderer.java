package mchorse.metamorph.capabilities.render;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.EntityModelHandler;
import mchorse.metamorph.client.render.MorphRenderPipeline;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Per-entity client model-renderer state (roadmap P54.1).
 *
 * <p>Legacy this was a Forge capability attached to every client-side living
 * entity; on Fabric an instance is held in {@link EntityModelHandler}'s
 * {@code WeakHashMap<LivingEntity, ModelRenderer>} (client-only feature, so no
 * common-set mixin is needed). Each instance re-evaluates the selector list
 * every 10 ticks and whenever the global {@link #selectorsUpdate} timestamp is
 * bumped by an editor edit, restores the entity's original width/height on
 * unmatch, and, when matched, drives the substituted morph's update loop.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/capabilities/render/ModelRenderer.java
 */
public class ModelRenderer
{
    /**
     * Global "selectors changed" timestamp. The editor bumps this (debounced)
     * so every per-entity renderer re-evaluates on the next tick.
     */
    public static long selectorsUpdate = System.currentTimeMillis();

    public EntitySelector selector;
    public AbstractMorph morph;
    public long lastUpdate = -1;

    private int timer;
    private float lastW = -1;
    private float lastH = -1;

    public void update(LivingEntity target)
    {
        if (this.lastUpdate < selectorsUpdate || this.isNotMatchedAnymore(target))
        {
            this.lastUpdate = selectorsUpdate;

            this.updateSelector(target);
        }

        if (this.selector != null && this.morph != null)
        {
            this.morph.update(target);
        }
    }

    private boolean isNotMatchedAnymore(LivingEntity target)
    {
        this.timer += 1;

        if (this.timer > 10)
        {
            this.timer = 0;

            if (this.selector == null)
            {
                return true;
            }

            return !this.selector.matches(target);
        }

        return false;
    }

    public void updateSelector(LivingEntity target)
    {
        this.selector = null;
        this.morph = null;

        for (EntitySelector selector : EntityModelHandler.selectors)
        {
            if (selector.matches(target))
            {
                this.selector = selector;
                this.morph = MorphManager.INSTANCE.morphFromNBT(this.selector.morph);

                if (this.lastW < 0)
                {
                    this.lastW = target.getWidth();
                    this.lastH = target.getHeight();
                }

                return;
            }
        }

        if (this.selector == null && this.lastW > 0 && this.lastH > 0)
        {
            /* SEAM(P62): legacy poked target.width/height back to the original
             * size here. 1.20.4 entity dimensions are computed via
             * getDimensions(), so the hitbox restore for NON-player selector
             * targets needs a LivingEntity getDimensions mixin (deferred — the
             * P54 dimension mixin is player-only). The bookkeeping is preserved
             * so the restore hooks in cleanly once that mixin lands. */
            this.lastW = -1;
            this.lastH = -1;
        }
    }

    public boolean canRender()
    {
        return this.selector != null;
    }

    /**
     * Render the selector's substituted morph for the given entity. Returns
     * whether vanilla rendering should be cancelled.
     *
     * <p>Legacy: {@code this.selector != null && MorphUtils.render(this.morph,
     * entity, x, y, z, 0, partialTicks)}. {@link MorphRenderPipeline#drawEntity}
     * is the port's {@code MorphUtils.render} — it opens the {@code
     * MorphRenderContext} frame, applies the {@code errorRendering} latch and
     * returns whether anything was actually drawn, so an unported morph type
     * still falls through to the vanilla entity rather than leaving a hole.</p>
     *
     * <p>The render frame ({@link MatrixStack} / {@link
     * VertexConsumerProvider} / packed light) is threaded down from the
     * {@code LivingEntityRenderer.render} mixin; 1.12.2's {@code x/y/z} are
     * kept in the signature because legacy's were the render offsets, and the
     * mixin passes the same {@code 0,0,0} legacy's {@code RenderLivingEvent}
     * did for an entity drawn at the matrix origin.</p>
     */
    public boolean render(LivingEntity entity, double x, double y, double z,
        MatrixStack matrices, VertexConsumerProvider consumers, int light, float partialTicks)
    {
        if (this.selector == null || this.morph == null)
        {
            return false;
        }

        return MorphRenderPipeline.drawEntity(this.morph, entity, matrices, consumers, light, partialTicks);
    }
}
