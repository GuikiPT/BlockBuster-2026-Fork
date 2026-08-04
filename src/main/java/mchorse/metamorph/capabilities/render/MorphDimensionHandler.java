package mchorse.metamorph.capabilities.render;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Installs the {@link AbstractMorph#sizeHandler} seam (roadmap P54).
 *
 * <p>{@code AbstractMorph.updateSizeDefault} computes the clamped
 * width/height/eye-height with the legacy math and hands them to this handler,
 * which stores them on the player's {@link IMorphDimensionProvider} and
 * triggers {@code calculateDimensions()} so the {@code getDimensions}/{@code
 * getActiveEyeHeight} mixins pick them up next frame. Non-player targets are
 * ignored here (the P54 dimension mixins are player-only).</p>
 */
public final class MorphDimensionHandler
{
    private MorphDimensionHandler()
    {}

    public static void install()
    {
        AbstractMorph.sizeHandler = new AbstractMorph.ISizeHandler()
        {
            @Override
            public void apply(LivingEntity target, float width, float height, float eyeHeight, boolean applyEye)
            {
                MorphDimensionHandler.apply(target, width, height, eyeHeight, applyEye);
            }

            @Override
            public void clear(LivingEntity target)
            {
                MorphDimensionHandler.clear(target);
            }
        };
    }

    private static void apply(LivingEntity target, float width, float height, float eyeHeight, boolean applyEye)
    {
        if (target instanceof PlayerEntity && target instanceof IMorphDimensionProvider)
        {
            IMorphDimensionProvider provider = (IMorphDimensionProvider) target;

            provider.metamorph$applyMorphSize(width, height, eyeHeight, applyEye);
            target.calculateDimensions();
        }
    }

    /**
     * The demorph half: clear the stored override and recompute, so
     * {@code getDimensions}/{@code getActiveEyeHeight} fall through to vanilla.
     * {@code calculateDimensions} is what actually resizes the bounding box —
     * without it the flag would be off but the stale box would survive until
     * something else happened to trigger a recompute.
     */
    private static void clear(LivingEntity target)
    {
        if (target instanceof PlayerEntity && target instanceof IMorphDimensionProvider)
        {
            ((IMorphDimensionProvider) target).metamorph$clearMorphSize();
            target.calculateDimensions();
        }
    }
}
