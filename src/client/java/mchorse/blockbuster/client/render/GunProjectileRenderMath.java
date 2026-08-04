package mchorse.blockbuster.client.render;

import mchorse.mclib.utils.Interpolations;

/**
 * Pure (no-GL) render math extracted from the legacy
 * {@code RenderGunProjectile.doRender} (P197), so the fade envelope, the
 * Z-fighting jitter and the yaw/pitch alignment angles are headlessly
 * verifiable.
 *
 * <p>Legacy source of truth:
 * {@code blockbuster-1.12/.../client/render/RenderGunProjectile.java}.</p>
 */
public final class GunProjectileRenderMath
{
    private GunProjectileRenderMath()
    {}

    /**
     * The fade-envelope scale over the projectile's lifetime, without the
     * jitter term. Legacy:
     * <pre>
     * float timer = ticksExisted + partialTicks;
     * float scale = envelope(min(timer, lifeSpan), 0, fadeIn, lifeSpan - fadeOut, lifeSpan);
     * if (vanish &amp;&amp; vanishDelay &gt; 0)
     *     scale = envelope(vanishDelay - partialTicks, 0, fadeOut, vanishDelay, vanishDelay);
     * </pre>
     */
    public static float envelopeScale(int ticksExisted, float partialTicks, int lifeSpan, int fadeIn, int fadeOut, boolean vanish, int vanishDelay)
    {
        float timer = ticksExisted + partialTicks;
        float clamped = timer > lifeSpan ? lifeSpan : timer;
        float scale = Interpolations.envelope(clamped, 0, fadeIn, lifeSpan - fadeOut, lifeSpan);

        if (vanish && vanishDelay > 0)
        {
            scale = Interpolations.envelope(vanishDelay - partialTicks, 0, fadeOut, vanishDelay, vanishDelay);
        }

        return scale;
    }

    /**
     * The deliberate anti-Z-fighting jitter term legacy adds to the scale so a
     * cluster of projectiles at one point does not shimmer identically:
     * {@code (entityId % 100) / 10000F}. Kept verbatim (quirk).
     */
    public static float jitter(int entityId)
    {
        return (entityId % 100) / 10000F;
    }

    /**
     * The full render scale = fade envelope + jitter. This is what legacy fed
     * into {@code GlStateManager.scale}.
     */
    public static float scale(int entityId, int ticksExisted, float partialTicks, int lifeSpan, int fadeIn, int fadeOut, boolean vanish, int vanishDelay)
    {
        return envelopeScale(ticksExisted, partialTicks, lifeSpan, fadeIn, fadeOut, vanish, vanishDelay) + jitter(entityId);
    }

    /**
     * Interpolated angle (yaw or pitch) between the previous and current tick,
     * matching {@code prev + (cur - prev) * partialTicks}.
     */
    public static float interpolate(float prev, float cur, float partialTicks)
    {
        return prev + (cur - prev) * partialTicks;
    }

    /**
     * The yaw rotation applied about the Y axis (degrees). Only applied when
     * {@code props.yaw}. Legacy: interpolated {@code rotationYaw}.
     */
    public static float yawAngle(float prevYaw, float yaw, float partialTicks)
    {
        return interpolate(prevYaw, yaw, partialTicks);
    }

    /**
     * The pitch rotation applied about the X axis (degrees). Only applied when
     * {@code props.pitch}. Legacy: {@code -(interpolated pitch) + 90}.
     */
    public static float pitchAngle(float prevPitch, float pitch, float partialTicks)
    {
        return -interpolate(prevPitch, pitch, partialTicks) + 90;
    }
}
