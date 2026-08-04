package mchorse.aperture.camera.data;

import com.google.common.base.MoreObjects;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Position class
 *
 * This class represents a point in the space with specified angle of view.
 *
 * Port notes (P169):
 * <ul>
 * <li>{@code apply(PlayerEntity)} writes position <b>twice</b> via two
 * different setters — legacy {@code setPositionAndRotation} (yarn
 * {@code updatePositionAndAngles}: clamps pitch, refreshes tracked pos) and
 * {@code setLocationAndAngles} (yarn {@code refreshPositionAndAngles}:
 * resets prev/lastTick values). The double-set is load-bearing for
 * seamless interpolation between distant cameras; do not collapse.</li>
 * <li>The {@code Math.max(y - eyeHeight, -64.0)} floor transfers literally —
 * the 1.20.4 world floor happens to be −64; do NOT modernize to
 * {@code world.getBottomY()} (see plan S15 P169).</li>
 * <li>{@code interpolate} uses {@code lerpYaw} for yaw only.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/data/Position.java
 */
public class Position
{
    /**
     * Zero position. Not allowed for modification
     */
    public static final Position ZERO = new Position(0, 0, 0, 0, 0);

    public Point point = new Point(0, 0, 0);
    public Angle angle = new Angle(0, 0);

    public static Position fromBytes(ByteBuf buffer)
    {
        return new Position(Point.fromBytes(buffer), Angle.fromBytes(buffer));
    }

    public Position()
    {}

    public Position(Point point, Angle angle)
    {
        this.point = point;
        this.angle = angle;
    }

    public Position(float x, float y, float z, float yaw, float pitch)
    {
        this.point.set(x, y, z);
        this.angle.set(yaw, pitch);
    }

    public Position(float x, float y, float z, float yaw, float pitch, float roll, float fov)
    {
        this.point.set(x, y, z);
        this.angle.set(yaw, pitch, roll, fov);
    }

    public Position(PlayerEntity player)
    {
        this.set(player);
    }

    public void set(Position position)
    {
        this.point.set(position.point);
        this.angle.set(position.angle);
    }

    public void set(PlayerEntity player)
    {
        this.point.set(player);
        this.angle.set(player);
    }

    public void copy(Position position)
    {
        this.point.set(position.point.x, position.point.y, position.point.z);
        this.angle.set(position.angle.yaw, position.angle.pitch, position.angle.roll, position.angle.fov);
    }

    public void apply(PlayerEntity player)
    {
        double y = Math.max(this.point.y - player.getStandingEyeHeight(), -64.0);

        player.updatePositionAndAngles(this.point.x, y, this.point.z, this.angle.yaw, this.angle.pitch);
        player.refreshPositionAndAngles(this.point.x, y, this.point.z, this.angle.yaw, this.angle.pitch);
        player.setVelocity(Vec3d.ZERO);
        player.setHeadYaw(this.angle.yaw);
        player.prevHeadYaw = this.angle.yaw;
    }

    public void interpolate(Position position, float factor)
    {
        this.point.x = Interpolations.lerp(this.point.x, position.point.x, factor);
        this.point.y = Interpolations.lerp(this.point.y, position.point.y, factor);
        this.point.z = Interpolations.lerp(this.point.z, position.point.z, factor);
        this.angle.yaw = Interpolations.lerpYaw(this.angle.yaw, position.angle.yaw, factor);
        this.angle.pitch = Interpolations.lerp(this.angle.pitch, position.angle.pitch, factor);
        this.angle.roll = Interpolations.lerp(this.angle.roll, position.angle.roll, factor);
        this.angle.fov = Interpolations.lerp(this.angle.fov, position.angle.fov, factor);
    }

    public void fromJSON(JsonObject element)
    {
        if (element.has("point") && element.get("point").isJsonObject() && element.has("angle") && element.get("angle").isJsonObject())
        {
            this.point.fromJSON(element.get("point").getAsJsonObject());
            this.angle.fromJSON(element.get("angle").getAsJsonObject());
        }
    }

    public JsonObject toJSON()
    {
        JsonObject object = new JsonObject();

        object.add("point", this.point.toJSON());
        object.add("angle", this.angle.toJSON());

        return object;
    }

    public void toBytes(ByteBuf buffer)
    {
        this.point.toBytes(buffer);
        this.angle.toBytes(buffer);
    }

    public Position copy()
    {
        return new Position(this.point.copy(), this.angle.copy());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Position)
        {
            Position position = (Position) obj;

            return this.angle.equals(position.angle) && this.point.equals(position.point);
        }

        return super.equals(obj);
    }

    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper(this).addValue(this.point).addValue(this.angle).toString();
    }
}
