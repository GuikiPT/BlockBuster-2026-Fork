package mchorse.aperture.camera.modifiers;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.data.Point;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.mclib.config.values.ValueBoolean;
import net.minecraft.entity.Entity;

/**
 * Follow modifier (P175) — binds camera position to the <b>averaged</b>
 * interpolated entity position + offset; {@code relative} subtracts the
 * fixture's tick-0 position so path motion stays entity-relative.
 * Verbatim port ({@code lastTickPos*} → yarn {@code prev*}).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/modifiers/FollowModifier.java
 */
public class FollowModifier extends EntityModifier
{
    public final ValueBoolean relative = new ValueBoolean("relative");

    public FollowModifier()
    {
        super();

        this.register(this.relative);
    }

    @Override
    public void modify(long ticks, long offset, AbstractFixture fixture, float partialTick, float previewPartialTick, CameraProfile profile, Position pos)
    {
        if (this.checkForDead())
        {
            this.tryFindingEntity();
        }

        if (this.entities == null)
        {
            return;
        }

        if (fixture != null && this.relative.get())
        {
            fixture.applyFixture(0, 0, 0, profile, this.position);
        }
        else
        {
            this.position.copy(pos);
        }

        double x = 0;
        double y = 0;
        double z = 0;
        int size = this.entities.size();

        for (Entity entity : this.entities)
        {
            /* P286: legacy read lastTickPos*, NOT prevPos* — the render-side
             * previous position. yarn's counterpart is lastRenderX/Y/Z, not
             * prevX/Y/Z; the two are distinct fields in both versions. */
            x += entity.lastRenderX + (entity.getX() - entity.lastRenderX) * partialTick;
            y += entity.lastRenderY + (entity.getY() - entity.lastRenderY) * partialTick;
            z += entity.lastRenderZ + (entity.getZ() - entity.lastRenderZ) * partialTick;
        }

        x = x / size + pos.point.x - this.position.point.x;
        y = y / size + pos.point.y - this.position.point.y;
        z = z / size + pos.point.z - this.position.point.z;

        Point point = this.offset.get();

        pos.point.set(x + point.x, y + point.y, z + point.z);
    }

    @Override
    public AbstractModifier create()
    {
        return new FollowModifier();
    }
}
