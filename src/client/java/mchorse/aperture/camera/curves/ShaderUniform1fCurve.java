package mchorse.aperture.camera.curves;

import mchorse.aperture.client.AsmShaderHandler;
import mchorse.mclib.client.gui.utils.keys.IKey;

/**
 * A shader curve driving one {@code float} uniform (roadmap P218).
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderUniform1fCurve.java}:
 * {@code apply} puts the value into {@link AsmShaderHandler#uniform1f} under the
 * raw uniform name, {@code reset} removes it. A present key means "a channel is
 * driving this uniform"; the Iris platform layer reads the map when it feeds its
 * custom uniforms, exactly where legacy's ASM hook in
 * {@code Shaders.setProgramUniform1f} read it.</p>
 *
 * <p>{@link #name} is the <b>uniform</b> name, not the curve id. For built-ins
 * they differ ({@code shader_rain_strength} drives {@code rainStrength}); for
 * option curves the uniform additionally carries
 * {@link AsmShaderHandler#uniformPrefix} — see {@link ShaderFloatOptionCurve}.</p>
 *
 * <p>Legacy resolved {@code getTranslatedName} through {@code I18n.format};
 * here it goes through {@link IKey} like the rest of the ported GUI. The key is
 * {@code aperture.gui.curves.shader.<snake_case uniform name>} — e.g.
 * {@code centerDepthSmooth} → {@code aperture.gui.curves.shader.center_depth_smooth}.</p>
 */
public class ShaderUniform1fCurve extends AbstractCurve
{
    public final String name;

    public ShaderUniform1fCurve(String name)
    {
        this.name = name;
    }

    @Override
    public String getTranslatedName()
    {
        return IKey.lang("aperture.gui.curves.shader." + this.convertTranslateKey(this.name)).get();
    }

    @Override
    public void apply(double value)
    {
        AsmShaderHandler.uniform1f.put(this.name, (float) value);
    }

    @Override
    public void reset()
    {
        AsmShaderHandler.uniform1f.remove(this.name);
    }
}
