package mchorse.aperture.camera.curves;

/**
 * Abstract rendering curve (P180).
 *
 * <p>Rendering curves modify some rendering parameter every frame during camera
 * playback / editor preview. Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/AbstractCurve.java}
 * — "For some reason, it's not a StructureBase" (legacy comment preserved).</p>
 */
public abstract class AbstractCurve
{
    public abstract String getTranslatedName();

    public abstract void apply(double value);

    public abstract void reset();

    /**
     * Convert a camelCase enum constant name into the snake_case l10n suffix
     * (e.g. {@code CelestialAngle} -> {@code celestial_angle}). Ported verbatim
     * so {@code aperture.gui.curves.*} keys resolve identically to 1.12.2.
     */
    public String convertTranslateKey(String str)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < str.length(); i++)
        {
            char c = str.charAt(i);

            if (c >= 'A' && c <= 'Z')
            {
                c += 'a' - 'A';

                if (i > 0)
                {
                    builder.append('_');
                }
            }

            builder.append(c);
        }

        return builder.toString();
    }
}
