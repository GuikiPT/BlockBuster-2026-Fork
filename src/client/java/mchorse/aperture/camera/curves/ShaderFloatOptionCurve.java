package mchorse.aperture.camera.curves;

import mchorse.aperture.camera.CurveManager;
import mchorse.aperture.client.AsmShaderHandler;

/**
 * A shader curve driving one discovered <b>float</b> shader-pack option
 * (roadmap P218).
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderFloatOptionCurve.java}.</p>
 *
 * <h2>The two names</h2>
 *
 * <p>This is the class where the {@code _uniform_} prefix quirk lives, and it is
 * a disk-format contract:</p>
 *
 * <ul>
 *   <li>{@link #id} is the pack's option name, e.g. {@code SHADOW_QUALITY}. The
 *       curve is registered under {@code "shader_" + id}, so the profile JSON
 *       key is {@code shader_SHADOW_QUALITY} — the same key 1.12.2 wrote.</li>
 *   <li>{@code super(AsmShaderHandler.uniformPrefix + id)} makes the inherited
 *       {@link #name} the <i>uniform</i> {@code _uniform_SHADOW_QUALITY}, which
 *       is what the source rewriter injects into the pack's GLSL
 *       ({@code #define SHADOW_QUALITY _uniform_SHADOW_QUALITY}). The prefix
 *       must never appear in a curve id.</li>
 * </ul>
 *
 * <p>{@link #getTranslatedName()} is legacy's: when the pack's own label for the
 * option differs from its identifier, show {@code "<label>/<id>"}, otherwise
 * just the id — never a lang key, because option names are pack-defined.
 * Legacy compared {@code option.getName()} against {@code option.getNameText()};
 * {@code getName()} <i>is</i> the id the option is looked up by, so the port
 * compares {@link #id} against
 * {@link mchorse.aperture.camera.CurveManager.ShaderOption#displayName()}.</p>
 */
public class ShaderFloatOptionCurve extends ShaderUniform1fCurve
{
    public final String id;

    public ShaderFloatOptionCurve(String id)
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
