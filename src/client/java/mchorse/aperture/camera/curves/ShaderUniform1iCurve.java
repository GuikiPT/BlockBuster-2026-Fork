package mchorse.aperture.camera.curves;

import mchorse.aperture.client.AsmShaderHandler;

/**
 * A shader curve driving one {@code int} uniform (roadmap P218).
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderUniform1iCurve.java}.
 * It extends the float curve purely to inherit {@link #name} and
 * {@link #getTranslatedName()}; both {@code apply} and {@code reset} are
 * overridden to use {@link AsmShaderHandler#uniform1i} instead — a class can
 * never write to both maps.</p>
 *
 * <p>The {@code (int) value} cast is a plain truncation toward zero, exactly as
 * legacy: {@code 1.9} becomes {@code 1}, {@code -1.9} becomes {@code -1}. The
 * world-time curve depends on this (see {@link ShaderWorldTimeCurve}).</p>
 */
public class ShaderUniform1iCurve extends ShaderUniform1fCurve
{
    public ShaderUniform1iCurve(String name)
    {
        super(name);
    }

    @Override
    public void apply(double value)
    {
        AsmShaderHandler.uniform1i.put(this.name, (int) value);
    }

    @Override
    public void reset()
    {
        AsmShaderHandler.uniform1i.remove(this.name);
    }
}
