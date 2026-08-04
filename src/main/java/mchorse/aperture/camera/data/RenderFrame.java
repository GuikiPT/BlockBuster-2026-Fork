package mchorse.aperture.camera.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import mchorse.aperture.Aperture;
import mchorse.mclib.utils.ICopy;
import mchorse.mclib.utils.Interpolations;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Per-frame recorded camera sample (used by {@code ManualFixture}).
 *
 * Port notes (P169):
 * <ul>
 * <li>JSON write form is the <b>8-element array</b>
 * {@code [x, y, z, yaw, pitch, roll, fov, pt]}; the legacy object form
 * ({@code {x..pt}}) is accepted on read only.</li>
 * <li>{@code fromPlayer} was {@code @SideOnly(CLIENT)}: roll/fov route
 * through the {@link Aperture#currentRoll}/{@link Aperture#currentFov}
 * seams; {@link #tick} is set by the recording GUI (legacy
 * {@code GuiManualFixturePanel.tick} — P184 owns that coupling).</li>
 * <li>Legacy multiplied fov by 0.25 while Optifine zoom was active at
 * capture time. P218.1 restored that code path behind the {@link #zooming}
 * seam: it is installed from the client entrypoint to
 * {@code mchorse.aperture.utils.OptifineHelper.isZooming()}, which answers
 * {@code false} on 1.20.4 (no Optifine zoom key exists — S21 open question
 * 4), so captured FOV is unchanged. The branch stays live so an opt-in zoom
 * bridge is a supplier install rather than a re-port.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/data/RenderFrame.java
 */
public class RenderFrame implements ICopy<RenderFrame>
{
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public float roll;
    public float fov;
    public float pt;

    /**
     * Used only during recording
     */
    public int tick;

    /**
     * P184 seam: the recording GUI's current tick — legacy
     * {@code fromPlayer} read {@code GuiManualFixturePanel.tick} (a client
     * GUI static) directly; the client wiring installs that supplier here.
     */
    public static IntSupplier currentRecordingTick = () -> 0;

    /**
     * P218.1 seam: legacy {@code OptifineHelper.isZooming()} (a client-only
     * Aperture class this main-source-set type cannot see). Default
     * {@code false} — which is also what the client installs, since 1.20.4 has
     * no Optifine zoom key; see that method's javadoc for why it is not mapped
     * onto a modern zoom mod.
     */
    public static BooleanSupplier zooming = () -> false;

    public RenderFrame()
    {}

    public RenderFrame(PlayerEntity player, float partialTicks)
    {
        this.fromPlayer(player, partialTicks);
    }

    public void position(double x, double y, double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void angle(float yaw, float pitch, float roll, float fov)
    {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.fov = fov;
    }

    public void apply(Position pos)
    {
        pos.point.set(this.x, this.y, this.z);
        pos.angle.set(this.yaw, this.pitch, this.roll, this.fov);
    }

    public void fromPlayer(PlayerEntity player, float partialTicks)
    {
        this.x = Interpolations.lerp(player.prevX, player.getX(), partialTicks);
        this.y = Interpolations.lerp(player.prevY, player.getY(), partialTicks);
        this.z = Interpolations.lerp(player.prevZ, player.getZ(), partialTicks);
        this.yaw = player.getYaw();
        this.pitch = player.getPitch();
        this.roll = Aperture.currentRoll.get();
        this.fov = Aperture.currentFov.get();
        this.pt = partialTicks;
        /* Legacy: this.tick = GuiManualFixturePanel.tick — routed through
         * the P184 seam so the main source set stays client-free */
        this.tick = currentRecordingTick.getAsInt();

        this.applyZoom();
    }

    /**
     * Legacy tail of {@code fromPlayer}:
     * {@code if (OptifineHelper.isZooming()) this.fov *= 0.25F;}.
     *
     * <p>Its own method purely so it is testable — {@code fromPlayer} needs a
     * real {@code PlayerEntity}, which cannot exist headlessly.</p>
     */
    public void applyZoom()
    {
        if (zooming.getAsBoolean())
        {
            this.fov *= 0.25F;
        }
    }

    @Override
    public RenderFrame copy()
    {
        RenderFrame frame = new RenderFrame();

        frame.copy(this);

        return frame;
    }

    @Override
    public void copy(RenderFrame origin)
    {
        this.position(origin.x, origin.y, origin.z);
        this.angle(origin.yaw, origin.pitch, origin.roll, origin.fov);
        this.pt = origin.pt;
    }

    public void fromJSON(JsonElement element)
    {
        if (element.isJsonArray() && element.getAsJsonArray().size() >= 8)
        {
            JsonArray array = element.getAsJsonArray();

            this.x = array.get(0).getAsDouble();
            this.y = array.get(1).getAsDouble();
            this.z = array.get(2).getAsDouble();
            this.yaw = array.get(3).getAsFloat();
            this.pitch = array.get(4).getAsFloat();
            this.roll = array.get(5).getAsFloat();
            this.fov = array.get(6).getAsFloat();
            this.pt = array.get(7).getAsFloat();
        }
        else if (element.isJsonObject())
        {
            JsonObject object = element.getAsJsonObject();

            this.x = object.get("x").getAsDouble();
            this.y = object.get("y").getAsDouble();
            this.z = object.get("z").getAsDouble();
            this.yaw = object.get("yaw").getAsFloat();
            this.pitch = object.get("pitch").getAsFloat();
            this.roll = object.get("roll").getAsFloat();
            this.fov = object.get("fov").getAsFloat();
            this.pt = object.get("pt").getAsFloat();
        }
    }

    public JsonArray toJSON()
    {
        JsonArray array = new JsonArray();

        array.add(this.x);
        array.add(this.y);
        array.add(this.z);
        array.add(this.yaw);
        array.add(this.pitch);
        array.add(this.roll);
        array.add(this.fov);
        array.add(this.pt);

        return array;
    }
}
