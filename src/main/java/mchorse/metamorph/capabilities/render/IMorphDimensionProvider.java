package mchorse.metamorph.capabilities.render;

/**
 * Duck interface holding a morph's requested size/eye-height on a player
 * (roadmap P54).
 *
 * <p>On 1.20.4 entity dimensions are pose-driven and cannot be poked into
 * {@code width}/{@code height} fields the way 1.12.2 did. Instead {@code
 * AbstractMorph.updateSizeDefault} routes through the {@code
 * AbstractMorph.sizeHandler} seam (installed by {@code MorphDimensionHandler})
 * which stores the requested size here; the {@code getDimensions}/{@code
 * getActiveEyeHeight} mixins on {@code PlayerEntity} then serve it. The exact
 * legacy clamp/eye math stays in {@code AbstractMorph.updateSizeDefault}.</p>
 */
public interface IMorphDimensionProvider
{
    /**
     * Store the morph's requested dimensions. {@code applyEye} is false for
     * non-player targets / when {@code disable_pov} is set (legacy: eye height
     * only overridden for players with POV enabled).
     */
    void metamorph$applyMorphSize(float width, float height, float eyeHeight, boolean applyEye);

    /**
     * Clear the morph size override so vanilla dimensions apply again (called
     * on demorph — SEAM(P52.1) wires the tick that invokes this).
     */
    void metamorph$clearMorphSize();

    boolean metamorph$hasMorphSize();

    float metamorph$morphWidth();

    float metamorph$morphHeight();

    float metamorph$morphEyeHeight();

    boolean metamorph$applyMorphEye();
}
