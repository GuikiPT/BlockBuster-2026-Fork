package mchorse.aperture.camera.smooth;

import mchorse.mclib.config.values.ValueFloat;
import mchorse.mclib.utils.Interpolations;

/**
 * Acceleration based linear filter (P179).
 *
 * Used for animation camera roll and FOV.
 *
 * Port notes: acceleration is zeroed in the {@code |acc| < 0.005} dead
 * zone; {@link #interpolate(float)} <b>mutates</b> the value while lerping
 * prev→value — exactly one call per frame is load-bearing. Verbatim.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/smooth/Filter.java
 */
public class Filter
{
    /**
     * Acceleration factor
     */
    public float acc;

    /**
     * Current value
     */
    public float value;

    /**
     * Previous value
     */
    public float prevValue;

    /**
     * Friction for acceleration, this value decides how fast acceleration
     * slow downs.
     */
    public ValueFloat friction;

    /**
     * Factor for acceleration (should be used externally)
     */
    public ValueFloat factor;

    /**
     * Set the value for the filter
     */
    public void set(float value)
    {
        this.value = this.prevValue = value;
    }

    /**
     * Reset the value
     *
     * Same thing as set, but also resetting the acceleration
     */
    public void reset(float value)
    {
        this.acc = 0.0F;
        this.value = this.prevValue = value;
    }

    /**
     * Accelerate the acceleration
     */
    public void accelerate(float value)
    {
        this.acc += value;
        this.acc *= this.friction.get();

        if (Math.abs(this.acc) < 0.005F)
        {
            this.acc = 0.0F;
        }
    }

    /**
     * Interpolate the value
     *
     * This method also changes the value of the filter, be careful with
     * it.
     */
    public float interpolate(float ticks)
    {
        float result;

        this.value += this.acc;
        result = Interpolations.lerp(this.prevValue, this.value, ticks);
        this.prevValue = this.value;

        return result;
    }
}
