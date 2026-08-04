package mchorse.aperture.camera.smooth;

import mchorse.aperture.Aperture;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueFloat;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Smooth camera (P179).
 *
 * This class is responsible for doing cool shit!
 *
 * Port notes: the {@code MouseFilter} magic coefficients
 * 0.975/0.95/0.90/0.875 are "the look" of the cinematic camera —
 * verbatim; it stays a nested class (legacy has no MouseFilter.java).
 * The {@code enabled}/{@code fricX}/{@code fricY} ValueFloat/Boolean
 * references are shared by reference from the Aperture config statics
 * (wired in ClientProxy, like legacy {@code ClientProxy.load}).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/smooth/SmoothCamera.java
 */
public class SmoothCamera
{
    public ValueBoolean enabled;

    public float yaw;
    public float pitch;

    public MouseFilter x = new MouseFilter();
    public MouseFilter y = new MouseFilter();

    public float accX;
    public float accY;

    public ValueFloat fricX;
    public ValueFloat fricY;

    public void update(PlayerEntity player, float dx, float dy)
    {
        this.accX += dx / 10.0F;
        this.accY += dy / 10.0F;

        this.accX *= this.fricX.get();
        this.accY *= this.fricY.get();

        this.yaw += this.accX;
        this.pitch += this.accY;

        if (Aperture.smoothClampPitch.get())
        {
            this.pitch = MathHelper.clamp(this.pitch, -90, 90);
        }

        this.x.update(this.yaw);
        this.y.update(this.pitch);
    }

    public void set(float yaw, float pitch)
    {
        this.yaw = yaw;
        this.pitch = pitch;

        this.accX = this.accY = 0.0F;

        this.x.set(yaw);
        this.y.set(pitch);
    }

    /**
     * Get interpolated yaw
     */
    public float getInterpYaw(float ticks)
    {
        return Interpolations.cubic(this.x.a, this.x.b, this.x.c, this.x.d, ticks);
    }

    /**
     * Get interpolated pitch
     */
    public float getInterpPitch(float ticks)
    {
        return Interpolations.cubic(this.y.a, this.y.b, this.y.c, this.y.d, ticks);
    }

    /**
     * Just like {@code net.minecraft.util.MouseFilter}, but only uses cubic
     * interolation.
     */
    public class MouseFilter
    {
        public float a;
        public float b;
        public float c;
        public float d;

        /**
         * Update variables to simulate cubic acceleration
         */
        public void update(float x)
        {
            float a = this.a;

            this.a = x - (x - a) * 0.975F;
            this.b = x - (x - a) * 0.95F;
            this.c = x - (x - a) * 0.90F;
            this.d = x - (x - a) * 0.875F;
        }

        /**
         * Set all values to given float
         */
        public void set(float x)
        {
            this.a = this.b = this.c = this.d = x;
        }
    }
}
