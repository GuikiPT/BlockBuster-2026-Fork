package mchorse.blockbuster.client.render;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Client-side seam for the actual gun-morph draw (P197).
 *
 * <p>The legacy gun render path called {@code MorphUtils.render(morph, entity,
 * 0, 0, 0, 0, partialTicks)} against a dummy actor. In the split source set the
 * concrete morph draw (matrices → vertex consumers) is the S6 client render
 * pipeline; this holder mirrors the {@code MorphRenderer.drawer} pattern so the
 * projectile renderer, the gun item renderer and the crosshair HUD all route
 * through one injectable seam.</p>
 *
 * <p>Null drawer → nothing is drawn (the totality rule on the render side: a gun
 * renders empty rather than crashing before S6 wires the pipeline). All the
 * transform math (envelope scale, jitter, yaw/pitch alignment, the gun
 * transforms, the {@code (0.5, 0, 0.5)} translate) is applied to the
 * {@link MatrixStack} <b>before</b> the draw call, so it is exercised (and
 * unit-tested via the pure math helpers) regardless of whether a drawer is
 * installed.</p>
 */
public final class GunMorphRenderer
{
    /** Installed by the S6 render pipeline; null → no draw. */
    public static Drawer drawer;

    private GunMorphRenderer()
    {}

    @FunctionalInterface
    public interface Drawer
    {
        void draw(AbstractMorph morph, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks);
    }

    /**
     * Draw a morph against the dummy actor at the current matrix position (the
     * caller has already applied the gun/projectile transform). No-op when no
     * drawer is installed or the morph/entity is null.
     */
    public static void draw(AbstractMorph morph, LivingEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float partialTicks)
    {
        if (drawer != null && morph != null && entity != null)
        {
            drawer.draw(morph, entity, matrices, vertexConsumers, light, partialTicks);
        }
    }

    /**
     * Screen-space crosshair morph draw (legacy {@code morph.renderOnScreen}).
     * Routed through the same seam holder; null → no-op.
     */
    public static ScreenDrawer screenDrawer;

    @FunctionalInterface
    public interface ScreenDrawer
    {
        void draw(AbstractMorph morph, int x, int y, float scale, float alpha);
    }

    public static void drawOnScreen(AbstractMorph morph, int x, int y, float scale, float alpha)
    {
        if (screenDrawer != null && morph != null)
        {
            screenDrawer.draw(morph, x, y, scale, alpha);
        }
    }
}
