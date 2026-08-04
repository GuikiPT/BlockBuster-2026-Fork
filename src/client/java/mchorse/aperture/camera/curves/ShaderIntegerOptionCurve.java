package mchorse.aperture.camera.curves;

import mchorse.aperture.camera.CurveManager;
import mchorse.aperture.client.AsmShaderHandler;

/**
 * A shader curve driving one discovered <b>integer</b> shader-pack option
 * (roadmap P218).
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderIntegerOptionCurve.java}
 * — identical to {@link ShaderFloatOptionCurve} except for the {@code 1i}
 * superclass. Legacy duplicated the body rather than sharing it (Java has no
 * multiple inheritance and the two uniform-curve classes are the split point);
 * the port keeps both classes with the legacy names so the diff stays 1:1.
 *
 * <p>See {@link ShaderFloatOptionCurve} for the {@code _uniform_} prefix
 * contract: curve id {@code shader_<OPTION>}, uniform
 * {@code _uniform_<OPTION>}.</p>
 */
public class ShaderIntegerOptionCurve extends ShaderUniform1iCurve
{
    public final String id;

    public ShaderIntegerOptionCurve(String id)
    {
        super(AsmShaderHandler.uniformPrefix + id);
        this.id = id;
    }

    @Override
    public String getTranslatedName()
    {
        CurveManager.ShaderOption option = CurveManager.findOption(this.id);

        if (option != null && !this.id.equals(option.displayName()))
        {
            return option.displayName() + "/" + this.id;
        }

        return this.id;
    }
}
