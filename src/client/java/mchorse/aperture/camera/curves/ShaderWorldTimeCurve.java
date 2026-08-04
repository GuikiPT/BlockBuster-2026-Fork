package mchorse.aperture.camera.curves;

/**
 * The world-time curve (roadmap P218): drives the {@code worldTime} integer
 * uniform, i.e. what time of day the shader pack thinks it is, independently of
 * the actual world.
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderWorldTimeCurve.java}.</p>
 *
 * <h2>The unit is hours — that is a disk-format contract</h2>
 *
 * <p>Keyframe values are <b>hours</b>, not ticks: the curve wraps into
 * {@code [0, 24)} and multiplies by {@code 1000}. Existing 1.12.2 profiles store
 * hours, so changing the unit would silently retime every shader-driven scene.
 * The wrap is legacy's {@code while} loop, kept literally (it is O(n) in whole
 * days for very negative input, which no keyframe ever is):</p>
 *
 * <pre>
 * while (value &lt; 0) value += 24.0;
 * super.apply((value % 24.0) * 1000.0);   // then (int)-truncated by the 1i curve
 * </pre>
 *
 * <p>Worked examples pinned by {@code ShaderCurvesTest}:
 * {@code -1 → 23000}, {@code 0 → 0}, {@code 24 → 0}, {@code 25 → 1000},
 * {@code -25 → 23000}.</p>
 */
public class ShaderWorldTimeCurve extends ShaderUniform1iCurve
{
    public ShaderWorldTimeCurve()
    {
        super("worldTime");
    }

    @Override
    public void apply(double value)
    {
        while (value < 0) value += 24.0;

        super.apply((value % 24.0) * 1000.0);
    }
}
