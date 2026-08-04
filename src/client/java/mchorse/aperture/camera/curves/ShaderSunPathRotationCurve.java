package mchorse.aperture.camera.curves;

import mchorse.aperture.client.AsmShaderHandler;

/**
 * The sun-path-rotation curve (roadmap P218): tilts the shader pack's celestial
 * path, i.e. where the sun rises and sets.
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderSunPathRotationCurve.java}.</p>
 *
 * <h2>Why this one writes twice</h2>
 *
 * <p>Every other shader curve only fills the uniform-override map and lets the
 * uniform push pick it up. {@code sunPathRotation} is different: the shader
 * runtime also keeps it as a plain field it derives the celestial matrix from,
 * so legacy assigned {@code Shaders.sunPathRotation = (float) value} in addition
 * to {@code super.apply(value)}. The field half is
 * {@link AsmShaderHandler#sunPathRotationSink} here.
 *
 * <p><b>On Iris that sink stays a no-op, and that is correct</b> (S22/P242):
 * Iris' live read is {@code PackDirectives.getSunPathRotation()}, which
 * {@code PackDirectivesMixin} overrides from the very same uniform-override map
 * {@code super.apply} just wrote — so the field write already happened, through
 * the other half. Pointing the sink at {@link AsmShaderHandler#sunPathRotation}
 * instead would clobber the captured pack default that {@link #reset()} restores
 * from. The seam survives for a future backend that really does keep a separate
 * live static.</p>
 *
 * <p>{@code reset()} restores {@link AsmShaderHandler#sunPathRotation}, the
 * value the <i>pack</i> asked for, captured at pack load. That is why the field
 * exists at all: without it a cleared channel would leave the pack tilted.</p>
 *
 * <h2>Double gate</h2>
 *
 * <p>This is the only shader curve registered behind <b>two</b> conditions — a
 * pack must be loaded <i>and</i> {@code Aperture.optifineShaderOptionCurve} must
 * be on. See {@link mchorse.aperture.camera.CurveManager#refreshCurves()}: the
 * config toggle governs the whole option-uniform machinery, and sun-path
 * rotation is delivered through it (it is a pack <i>option</i>, not a built-in
 * uniform, on the Optifine side).</p>
 */
public class ShaderSunPathRotationCurve extends ShaderUniform1fCurve
{
    public ShaderSunPathRotationCurve()
    {
        super("sunPathRotation");
    }

    @Override
    public void apply(double value)
    {
        super.apply(value);

        /* Legacy: Shaders.sunPathRotation = (float) value; — the cast is kept
         * here so the platform layer receives exactly the narrowed value. */
        AsmShaderHandler.sunPathRotationSink.accept((float) value);
    }

    @Override
    public void reset()
    {
        super.reset();

        AsmShaderHandler.sunPathRotationSink.accept(AsmShaderHandler.sunPathRotation);
    }
}
