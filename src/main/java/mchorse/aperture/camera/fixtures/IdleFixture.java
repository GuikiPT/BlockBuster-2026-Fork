package mchorse.aperture.camera.fixtures;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.values.ValuePosition;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Idle camera fixture (P171) — verbatim port.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/fixtures/IdleFixture.java
 */
public class IdleFixture extends AbstractFixture
{
    public final ValuePosition position = new ValuePosition("position");

    public IdleFixture(long duration)
    {
        super(duration);

        this.register(this.position);
    }

    @Override
    public void fromPlayer(PlayerEntity player)
    {
        this.position.get().set(player);
    }

    @Override
    public void applyFixture(long ticks, float partialTicks, float previewPartialTick, CameraProfile profile, Position pos)
    {
        pos.copy(this.position.get());
    }

    @Override
    public AbstractFixture create(long duration)
    {
        return new IdleFixture(duration);
    }
}
