package mchorse.aperture.client.gui;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.data.Position;
import mchorse.mclib.utils.Keys;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Camera editor flight mode (P179 — math + input seams slice).
 *
 * Port notes:
 * <ul>
 * <li>The math is verbatim: Ctrl = 5x / Alt = 0.2x multipliers, movement
 * factor {@code 0.1 * speed/1000 * multiplier}, angle factor
 * {@code 0.35 * speed/1000 * multiplier}, drag = 0.35 * multiplier per
 * pixel (LMB yaw/pitch, RMB roll, MMB FOV), speed 1–50000 with log-scale
 * increments (1/10/100/1000) and a 100 ms repeat delay, orbit distance
 * 0–100 with its own zoom steps, orbit origin recomputed via vecmath
 * {@code Matrix3d} pitch/yaw rotation.</li>
 * <li>Key polling goes through {@link #keys} (defaults to McLib's
 * {@code Keys.keyDownPoller}, GLFW-backed in-game, no-op headless);
 * modifier state through {@link #ctrl}/{@link #alt}.</li>
 * <li>The {@code GuiCameraEditor} coupling (editor position source for
 * orbit origin, {@code IGuiElement} membership, APIcons movement-type
 * icons, {@code drawSpeed} HUD) is P183 — {@link #positionSupplier} stands
 * in for {@code editor.position}; mouse state arrives via the
 * {@code animate(...)} parameters instead of {@code GuiContext}.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/Flight.java
 */
public class Flight
{
    /** Key-down poll seam (GLFW code → held). */
    public IntPredicate keys = code -> Keys.isKeyDown(code);

    /** Ctrl/Alt modifier polls (editor screen state, P183). */
    public Supplier<Boolean> ctrl = () -> false;
    public Supplier<Boolean> alt = () -> false;

    /** P183 seam: the editor's current position ({@code editor.position}). */
    public Supplier<Position> positionSupplier;

    private boolean enabled;
    private MovementType type = MovementType.HORIZONTAL;
    private int speed = 1000;

    private int dragging = -1;
    private int lastX;
    private int lastY;
    private long lastSpeed;

    private Vector3d lastPosition = new Vector3d();
    private float distance;
    private boolean update;

    public boolean isFlightEnabled()
    {
        return this.enabled;
    }

    public void setFlightEnabled(boolean enabled)
    {
        this.enabled = enabled;
        this.calculateOrigin();
    }

    public int getSpeed()
    {
        return this.speed;
    }

    public void setSpeed(int speed)
    {
        this.speed = MathHelper.clamp(speed, 1, 50000);
    }

    public float getDistance()
    {
        return this.distance;
    }

    public void toggleMovementType()
    {
        MovementType[] values = MovementType.values();
        int direction = this.alt.get() ? -1 : 1;

        this.setMovementType(values[MathUtils.cycler(this.type.ordinal() + direction, 0, values.length - 1)]);
    }

    public void setMovementType(MovementType type)
    {
        this.type = type;
        this.calculateOrigin();
    }

    public MovementType getMovementType()
    {
        return this.type;
    }

    private void calculateOrigin()
    {
        if (this.type != MovementType.ORBIT)
        {
            return;
        }

        Position position = this.positionSupplier == null ? null : this.positionSupplier.get();

        if (position == null)
        {
            return;
        }

        Vec3d vec = new Vec3d(0, 0, this.distance);
        double x = position.point.x;
        double y = position.point.y;
        double z = position.point.z;

        vec = vec.rotateX(-position.angle.pitch / 180 * (float) Math.PI);
        vec = vec.rotateY(-position.angle.yaw / 180 * (float) Math.PI);

        x += vec.x;
        y += vec.y;
        z += vec.z;

        this.lastPosition.set(x, y, z);
    }

    public float getSpeedFactor(int direction)
    {
        float factor = 1000;
        boolean zoomIn = direction > 0;

        if ((zoomIn && this.speed <= 10) || (!zoomIn && this.speed < 10))
        {
            factor = 1;
        }
        else if ((zoomIn && this.speed <= 100) || (!zoomIn && this.speed < 100))
        {
            factor = 10;
        }
        else if ((zoomIn && this.speed <= 1000) || (!zoomIn && this.speed < 1000))
        {
            factor = 100;
        }

        return factor;
    }

    public void mouseClicked(int mouseButton)
    {
        this.dragging = mouseButton;
    }

    public void mouseScrolled(int mouseWheel)
    {
        if (!this.enabled)
        {
            return;
        }

        boolean isAlt = this.alt.get();

        if (isAlt && this.type == MovementType.ORBIT)
        {
            this.distance += Math.copySign(this.getZoomFactor(), mouseWheel);
            this.distance = MathUtils.clamp(this.distance, 0, 100);
            this.update = true;
        }
        else if (!isAlt)
        {
            this.speed -= Math.copySign(this.getSpeedFactor(mouseWheel), mouseWheel);
            this.speed = MathHelper.clamp(this.speed, 1, 50000);
        }
    }

    public float getZoomFactor()
    {
        if (this.distance < 1) return 0.05F;
        if (this.distance > 30) return 5F;
        if (this.distance > 10) return 1F;
        if (this.distance > 3) return 0.5F;

        return 0.1F;
    }

    public void mouseReleased()
    {
        this.dragging = -1;
    }

    /**
     * Per-frame flight animation (legacy {@code animate(GuiContext,
     * Position)} — mouse coordinates passed explicitly).
     */
    public void animate(int mouseX, int mouseY, Position position, boolean guiFocused)
    {
        if (!this.enabled || guiFocused)
        {
            this.lastX = mouseX;
            this.lastY = mouseY;

            return;
        }

        float f = this.speed / 1000F;
        float multiplier = 1F;

        if (this.ctrl.get())
        {
            multiplier *= 5;
        }
        else if (this.alt.get())
        {
            multiplier *= 0.2F;
        }

        double factor = 0.1 * f * multiplier;
        double angleFactor = 0.35 * f * multiplier;

        float yaw = position.angle.yaw;
        float pitch = position.angle.pitch;
        float roll = position.angle.roll;
        float fov = position.angle.fov;
        boolean gotDragged = false;

        if (this.dragging != -1)
        {
            if (this.dragging == 0)
            {
                yaw += (mouseX - this.lastX) * (multiplier * 0.35F);
                pitch += (mouseY - this.lastY) * (multiplier * 0.35F);
            }
            else if (this.dragging == 1)
            {
                roll += (mouseX - this.lastX) * (multiplier * 0.35F);
            }
            else if (this.dragging == 2)
            {
                fov += (mouseY - this.lastY) * (multiplier * 0.35F);
            }

            gotDragged = true;
        }

        if (this.keys.test(Aperture.flightCameraUp.get()) || this.keys.test(Aperture.flightCameraDown.get()))
        {
            pitch += (this.keys.test(Aperture.flightCameraUp.get()) ? -angleFactor : angleFactor);
        }

        if (this.keys.test(Aperture.flightCameraLeft.get()) || this.keys.test(Aperture.flightCameraRight.get()))
        {
            yaw += (this.keys.test(Aperture.flightCameraLeft.get()) ? -angleFactor : angleFactor);
        }

        if (this.keys.test(Aperture.flightCameraRollMinus.get()) || this.keys.test(Aperture.flightCameraRollPlus.get()))
        {
            roll += (this.keys.test(Aperture.flightCameraRollMinus.get()) ? -angleFactor : angleFactor);
        }

        if (this.keys.test(Aperture.flightCameraFovMinus.get()) || this.keys.test(Aperture.flightCameraFovPlus.get()))
        {
            fov += (this.keys.test(Aperture.flightCameraFovMinus.get()) ? -angleFactor : angleFactor);
        }

        double x = position.point.x;
        double y = position.point.y;
        double z = position.point.z;

        double xx = 0;
        double yy = 0;
        double zz = 0;

        if (this.keys.test(Aperture.flightUp.get()) || this.keys.test(Aperture.flightDown.get()))
        {
            yy = (this.keys.test(Aperture.flightUp.get()) ? factor : -factor);
        }

        if (this.keys.test(Aperture.flightLeft.get()) || this.keys.test(Aperture.flightRight.get()))
        {
            xx = (this.keys.test(Aperture.flightLeft.get()) ? factor : -factor);
        }

        if (this.keys.test(Aperture.flightForward.get()) || this.keys.test(Aperture.flightBackward.get()))
        {
            zz = (this.keys.test(Aperture.flightForward.get()) ? factor : -factor);
        }

        if (gotDragged && this.type == MovementType.ORBIT || this.update)
        {
            Vector3d vec = new Vector3d(0, 0, this.distance);

            Matrix3d mat = new Matrix3d();
            mat.rotX((-pitch) / 180 * (float) Math.PI);
            mat.transform(vec);
            mat.rotY((180 - yaw) / 180 * (float) Math.PI);
            mat.transform(vec);

            position.point.set(this.lastPosition.x + vec.x, this.lastPosition.y + vec.y, this.lastPosition.z + vec.z);
            position.angle.set(yaw, pitch, roll, fov);

            this.update = false;
        }
        else if (xx != 0 || yy != 0 || zz != 0 || yaw != position.angle.yaw || pitch != position.angle.pitch || roll != position.angle.roll || fov != position.angle.fov)
        {
            Vec3d vec = new Vec3d(xx, yy, zz);

            if (this.type == MovementType.VERTICAL || this.type == MovementType.ORBIT)
            {
                vec = vec.rotateX(-pitch / 180 * (float) Math.PI);
            }

            vec = vec.rotateY(-yaw / 180 * (float) Math.PI);

            x += vec.x;
            y += vec.y;
            z += vec.z;

            position.point.set(x, y, z);
            position.angle.set(yaw, pitch, roll, fov);

            this.calculateOrigin();
        }

        int speedFactor = 0;
        int speedDelay = (int) (100 / multiplier);

        if (this.keys.test(Aperture.flightCameraSpeedPlus.get()) || this.keys.test(Aperture.flightCameraSpeedMinus.get()))
        {
            speedFactor = this.keys.test(Aperture.flightCameraSpeedPlus.get()) ? 1 : -1;
        }

        if (speedFactor != 0 && System.currentTimeMillis() > this.lastSpeed + speedDelay)
        {
            this.speed -= Math.copySign(this.getSpeedFactor(speedFactor), speedFactor);
            this.speed = MathHelper.clamp(this.speed, 1, 50000);
            this.lastSpeed = System.currentTimeMillis();
        }

        this.lastX = mouseX;
        this.lastY = mouseY;
    }

    /**
     * Movement types (plane/helicopter/orbit). The APIcons icons attach
     * with P183.
     */
    public static enum MovementType
    {
        HORIZONTAL, VERTICAL, ORBIT;
    }
}
