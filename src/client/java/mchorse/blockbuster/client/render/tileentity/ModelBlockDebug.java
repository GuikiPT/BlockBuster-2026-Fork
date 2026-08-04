package mchorse.blockbuster.client.render.tileentity;

/**
 * Pure math for the model-block F3 debug overlay (roadmap P96).
 *
 * <p>The legacy {@code TileEntityModelRenderer.render} debug branch draws a
 * status cube (teal enabled / orange disabled / red erroring), a white marker
 * cube at the settings offset, and — when the offset distance exceeds
 * {@code 0.1} — a black connecting beam whose orientation is computed by the
 * (deliberately convoluted) formulas below. Copied verbatim so the visual
 * matches 1.12.2.</p>
 */
public final class ModelBlockDebug
{
    /** Distance threshold above which the connecting beam is drawn. */
    public static final double BEAM_THRESHOLD = 0.1;

    private ModelBlockDebug()
    {}

    /**
     * Status-cube RGB (order {r, g, b}). Teal {@code (0, 0.5, 1)} when enabled,
     * orange {@code (1, 0.85, 0)} when disabled; overridden to red
     * {@code (1, 0, 0)} when the morph is erroring.
     */
    public static float[] statusColor(boolean enabled, boolean errorRendering)
    {
        float r = enabled ? 0F : 1F;
        float g = enabled ? 0.5F : 0.85F;
        float b = enabled ? 1F : 0F;

        if (errorRendering)
        {
            r = 1F;
            g = b = 0F;
        }

        return new float[] {r, g, b};
    }

    /** Straight-line distance from the block centre to the settings offset. */
    public static double distance(float x, float y, float z)
    {
        /* Legacy: MathHelper.sqrt(Vec3d.ZERO.squareDistanceTo(x, y, z)). */
        return Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    }

    /** Whether the connecting beam is drawn (offset distance &gt; 0.1). */
    public static boolean shouldDrawBeam(float x, float y, float z)
    {
        return distance(x, y, z) > BEAM_THRESHOLD;
    }

    /** Horizontal component of the offset ({@code sqrt(x² + z²)}). */
    public static double horizontalDistance(float x, float z)
    {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /**
     * Beam yaw in degrees. Legacy formula (verbatim):
     * {@code 180 - atan2(z, x) * 180/π + 90}.
     */
    public static double beamYaw(float x, float z)
    {
        return 180 - Math.atan2(z, x) * 180 / Math.PI + 90;
    }

    /**
     * Beam pitch in degrees. Legacy formula (verbatim):
     * {@code atan2(y, horizontalDistance) * 180/π}.
     */
    public static double beamPitch(float x, float y, float z)
    {
        return Math.atan2(y, horizontalDistance(x, z)) * 180 / Math.PI;
    }
}
