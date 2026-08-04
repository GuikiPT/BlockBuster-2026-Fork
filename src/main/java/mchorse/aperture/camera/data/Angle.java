package mchorse.aperture.camera.data;

import com.google.common.base.MoreObjects;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import mchorse.aperture.Aperture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Angle class
 *
 * Represents a camera angle: yaw, pitch and roll, and also Field of
 * View angle.
 *
 * Port notes (P169):
 * <ul>
 * <li>{@code fov} defaults to 70 — a freshly constructed Angle serializes
 * fov=70, not 0 (load-bearing).</li>
 * <li>{@code set(float, float)} clamps pitch to ±90 only when the
 * {@code smooth.clamp} config is on, null-guarded exactly like legacy
 * (data classes load before config in tests).</li>
 * <li>{@code angle(dx, dy, dz)} keeps 1.12's {@code MathHelper.sqrt} float
 * truncation ({@code (float) Math.sqrt}) and the fast
 * {@code MathHelper.atan2} approximation for float parity.</li>
 * <li>{@code set(PlayerEntity)} read {@code ClientProxy.control.roll} and
 * {@code gameSettings.fovSetting} (client-only); the port routes those
 * through the {@link Aperture#currentRoll}/{@link Aperture#currentFov}
 * seams installed by the client entrypoint.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/data/Angle.java
 */
public class Angle
{
    public float yaw;
    public float pitch;
    public float roll;
    public float fov = 70;

    public static Angle fromBytes(ByteBuf buffer)
    {
        return new Angle(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public static Angle angle(Point a, Point b)
    {
        return angle(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static Angle angle(double dx, double dy, double dz)
    {
        /* 1.12's MathHelper.sqrt(double) returned float — keep the truncation */
        double horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        double yaw = MathHelper.atan2(dz, dx) * 180D / Math.PI - 90;
        double pitch = -(MathHelper.atan2(dy, horizontalDistance) * 180D / Math.PI);

        return new Angle((float) yaw, (float) pitch);
    }

    public Angle(float yaw, float pitch, float roll, float fov)
    {
        this.set(yaw, pitch, roll, fov);
    }

    public Angle(float yaw, float pitch)
    {
        this.set(yaw, pitch);
    }

    public void set(Angle angle)
    {
        this.set(angle.yaw, angle.pitch, angle.roll, angle.fov);
    }

    public void set(float yaw, float pitch, float roll, float fov)
    {
        this.set(yaw, pitch);
        this.roll = roll;
        this.fov = fov;
    }

    public void set(float yaw, float pitch)
    {
        if (Aperture.smoothClampPitch != null && Aperture.smoothClampPitch.get())
        {
            /* Clamp pitch */
            pitch = MathHelper.clamp(pitch, -90, 90);
        }

        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void set(PlayerEntity player)
    {
        float fov = Aperture.currentFov.get();

        this.set(player.getYaw(), player.getPitch(), Aperture.currentRoll.get(), fov);
    }

    public void fromJSON(JsonObject element)
    {
        this.yaw = element.get("yaw").getAsFloat();
        this.pitch = element.get("pitch").getAsFloat();
        this.roll = element.get("roll").getAsFloat();
        this.fov = element.get("fov").getAsFloat();
    }

    public JsonObject toJSON()
    {
        JsonObject object = new JsonObject();

        object.addProperty("yaw", this.yaw);
        object.addProperty("pitch", this.pitch);
        object.addProperty("roll", this.roll);
        object.addProperty("fov", this.fov);

        return object;
    }

    public void toBytes(ByteBuf buffer)
    {
        buffer.writeFloat(this.yaw);
        buffer.writeFloat(this.pitch);
        buffer.writeFloat(this.roll);
        buffer.writeFloat(this.fov);
    }

    public Angle copy()
    {
        return new Angle(this.yaw, this.pitch, this.roll, this.fov);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Angle)
        {
            Angle angle = (Angle) obj;

            return this.yaw == angle.yaw && this.pitch == angle.pitch && this.roll == angle.roll && this.fov == angle.fov;
        }

        return super.equals(obj);
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this).addValue(this.yaw).addValue(this.pitch).addValue(this.roll).addValue(this.fov).toString();
    }
}
