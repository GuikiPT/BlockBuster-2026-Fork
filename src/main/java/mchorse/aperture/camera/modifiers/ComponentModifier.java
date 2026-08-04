package mchorse.aperture.camera.modifiers;

import mchorse.mclib.config.values.ValueInt;

/**
 * Abstract component modifier (P174) — the {@code active} bitmask layout
 * (bit 0=x, 1=y, 2=z, 3=yaw, 4=pitch, 5=roll, 6=fov) is a save-format
 * contract shared by Shake/Drag/Math.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/modifiers/ComponentModifier.java
 */
public abstract class ComponentModifier extends AbstractModifier
{
    /**
     * Active value that uses only 7 bits for determining which components
     * should be processed.
     */
    public final ValueInt active = new ValueInt("active");

    public ComponentModifier()
    {
        super();

        this.register(this.active);
    }

    /**
     * Whether current given bit is 1
     */
    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }
}
