package mchorse.aperture.client;

import java.util.HashMap;

/**
 * P180 — render-override sink for the vanilla-safe curve subset.
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/AsmRenderingHandler.java}.
 * In 1.12.2 this class was the redirect target the Aperture coremod
 * ({@code WorldTransformer}/{@code GlStateManagerTransformer}) rewrote
 * {@code World.getSkyColor/getCloudColour/getFogColor/getCelestialAngle} and
 * {@code GlStateManager.setFogDensity/Start/End} into. On 1.20.4 the coremod is
 * replaced by mixins ({@code ClientWorldMixin}, {@code WorldCelestialMixin},
 * {@code BackgroundRendererMixin}), but the sink shape is preserved verbatim so
 * the {@link mchorse.aperture.camera.curves.VanillaAsmCurve} apply/reset logic
 * diffs 1:1 against legacy: {@link #values} holds the currently-overridden curve
 * values, {@link #getOption(Curve, double)} falls back to the vanilla value when
 * a curve has no override.</p>
 *
 * <p>The old ASM hook methods that took a {@code WorldProvider} were dropped —
 * they were 1.12.2 method-body replacements with no 1.20.4 analogue; the mixins
 * call {@link #getOption(Curve, double)} directly instead.</p>
 */
public class AsmRenderingHandler
{
    /**
     * Currently-overridden curve values (populated by {@code VanillaAsmCurve.apply},
     * removed by {@code VanillaAsmCurve.reset}). Empty means "no override active"
     * so every render hook falls through to the vanilla value.
     */
    public static final HashMap<Curve, Double> values = new HashMap<Curve, Double>();

    /**
     * Return the overridden value for {@code option}, or {@code defaultValue}
     * (the vanilla value) when no curve is driving it. Verbatim legacy semantics.
     */
    public static double getOption(Curve option, double defaultValue)
    {
        Double value = values.get(option);

        if (value != null)
        {
            return value;
        }
        else
        {
            return defaultValue;
        }
    }

    /**
     * Legacy celestial-angle wrap (from {@code AsmRenderingHandler.getCelestialAngle}):
     * the override is stored in degrees (0..360); wrap it into {@code [0, 360)}
     * then normalize to a {@code [0, 1)} fraction. On 1.20.4 the render side
     * multiplies the fraction by {@code 2*PI} for {@code getSkyAngleRadians}.
     *
     * <p>Kept as a pure static so the wrap math is unit-testable without any
     * Minecraft bootstrap.</p>
     */
    public static float celestialFraction(double degrees)
    {
        double angle = degrees % 360.0;

        if (angle < 0)
        {
            angle += 360.0;
        }

        return (float) (angle / 360.0);
    }

    /**
     * The 13 vanilla-render curve channels. Declaration order is a soft contract
     * (drives the lowercased id table {@code skyr..celestialangle} in
     * {@link mchorse.aperture.camera.CurveManager}); do not reorder.
     */
    public static enum Curve
    {
        SkyR, SkyG, SkyB,
        CloudR, CloudG, CloudB,
        FogR, FogG, FogB,
        FogStart, FogEnd, FogDensity,
        CelestialAngle;
    }
}
