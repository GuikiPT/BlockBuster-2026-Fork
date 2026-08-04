package mchorse.aperture.camera.modifiers;

import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.data.Position;
import mchorse.aperture.camera.data.StructureBase;
import mchorse.aperture.camera.fixtures.AbstractFixture;
import mchorse.aperture.camera.values.ValueEnvelope;
import mchorse.aperture.camera.values.ValueModifiers;
import mchorse.mclib.config.values.ValueBoolean;

/**
 * Abstract camera modifier (P174).
 *
 * Port notes: {@code applyModifiers} keeps the legacy <b>static shared
 * temporary Position</b> — the apply path is single-threaded (client render
 * thread); the envelope factor is computed at
 * {@code offset + previewPartialTick} (paused-editor preview) and the
 * modified position is lerped in by that factor (skipped at 0). Returns
 * false when the {@code target} modifier short-circuits — consumed by
 * {@code CameraProfile.applyProfile}'s global-modifier gate (P177).
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/modifiers/AbstractModifier.java
 */
public abstract class AbstractModifier extends StructureBase
{
    public static final Position temporary = new Position();

    /**
     * Whether this modifier is enabled
     */
    public final ValueBoolean enabled = new ValueBoolean("enabled", true);

    /**
     * Envelope configuration
     */
    public final ValueEnvelope envelope = new ValueEnvelope("envelope");

    /**
     * Apply camera modifiers
     */
    public static boolean applyModifiers(CameraProfile profile, AbstractFixture fixture, long ticks, long offset, float partialTick, float previewPartialTick, AbstractModifier target, Position pos)
    {
        long duration = fixture == null ? profile.getDuration() : fixture.getDuration();
        ValueModifiers modifiers = fixture == null ? profile.modifiers : fixture.modifiers;

        for (int i = 0; i < modifiers.size(); i++)
        {
            AbstractModifier modifier = modifiers.get(i);

            if (modifier == target)
            {
                return false;
            }

            if (!modifier.enabled.get())
            {
                continue;
            }

            float factor = modifier.envelope.get().factorEnabled(duration, offset + previewPartialTick);

            temporary.copy(pos);
            modifier.modify(ticks, offset, fixture, partialTick, previewPartialTick, profile, temporary);

            if (factor != 0)
            {
                pos.interpolate(temporary, factor);
            }
        }

        return true;
    }

    public AbstractModifier()
    {
        this.register(this.enabled);
        this.register(this.envelope);
    }

    /**
     * Modify (apply, filter, process, however you name it) modifier on given position
     *
     * @param ticks - Amount of ticks from start
     * @param offset - Amount of ticks from current camera fixture
     * @param fixture - Currently running camera fixture
     */
    public abstract void modify(long ticks, long offset, AbstractFixture fixture, float partialTick, float previewPartialTick, CameraProfile profile, Position pos);

    public final AbstractModifier copy()
    {
        AbstractModifier modifier = this.create();

        modifier.copy(this);

        return modifier;
    }

    public abstract AbstractModifier create();

    public void breakDown(AbstractModifier original, long offset, long duration)
    {
        this.envelope.get().breakDown(original.envelope.get(), offset, duration);
    }
}
