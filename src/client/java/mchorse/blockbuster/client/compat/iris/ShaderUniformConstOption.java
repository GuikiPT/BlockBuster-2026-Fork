package mchorse.blockbuster.client.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A shader-pack {@code const} option that Aperture can turn into a live uniform
 * (S21 P218).
 *
 * <p>Legacy source: {@code AsmShaderHandler$ShaderUniformConstOption}, which
 * extended Optifine's {@code ShaderOptionVariableConst}. As with
 * {@link ShaderUniformOption}, the base class does not exist on 1.20.4, so
 * {@code ShaderOptionVariableConst.PATTERN_CONST}, its {@code matchesLine} and
 * its {@code getSourceLine()} are inlined verbatim.</p>
 *
 * <p><b>Exactly one const option is ever eligible:</b> {@code sunPathRotation}.
 * That is not a heuristic — it is a literal {@code "sunPathRotation".equals(name)}
 * in legacy, because it is the only pack const the shader pipeline re-reads
 * every frame rather than baking into the compiled program. Every other
 * {@code const} stays a compile-time constant and its curve id is never
 * registered.</p>
 *
 * <p>Note that the const arm of the rewriter emits the uniform with the
 * <b>bare</b> option name (no {@code _uniform_} prefix): the const declaration
 * itself is commented out, so the name is free and the shader body needs no
 * {@code #define} indirection. {@link ShaderUniformOption} needs the prefix
 * precisely because its {@code #define} has to survive.</p>
 */
public class ShaderUniformConstOption
{
    /** Optifine's {@code ShaderOptionVariableConst.PATTERN_CONST}, verbatim. */
    public static final Pattern PATTERN_CONST = Pattern.compile("^\\s*const\\s*(float|int)\\s*([A-Za-z0-9_]+)\\s*=\\s*(-?[0-9\\.]+f?F?)\\s*;\\s*(//.*)?$");

    /** The only const name legacy ever promoted to a uniform. */
    public static final String SUN_PATH_ROTATION = "sunPathRotation";

    /** @see ShaderUniformOption the {@code doPatch} latch */
    public static boolean doPatch = false;

    public final String name;
    public final String type;
    public final String description;
    public final String[] values;
    public final String path;

    /** {@code "sunPathRotation".equals(name)} — the whole eligibility rule. */
    public final boolean isUniform;

    public String value;

    private boolean enabled = true;
    private boolean visible;
    private String nameText;

    public ShaderUniformConstOption(String name, String type, String description, String value, String[] values, String path)
    {
        this.name = name;
        this.type = type;
        this.description = description;
        this.value = value;
        this.values = values == null ? new String[] {value} : values;
        this.path = path;
        this.nameText = name;
        this.visible = this.values.length > 1;
        this.isUniform = SUN_PATH_ROTATION.equals(name);
    }

    public String getName()
    {
        return this.name;
    }

    public String getValue()
    {
        return this.value;
    }

    public String[] getValues()
    {
        return this.values;
    }

    public String getNameText()
    {
        return this.nameText;
    }

    public void setNameText(String nameText)
    {
        this.nameText = nameText == null ? this.name : nameText;
    }

    public boolean isEnabled()
    {
        return this.enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public boolean isVisible()
    {
        return this.visible;
    }

    public boolean matchesLine(String line)
    {
        if (this.isUniform() && !doPatch)
        {
            return false;
        }

        Matcher matcher = PATTERN_CONST.matcher(line);

        if (!matcher.matches())
        {
            return false;
        }

        String captured = matcher.group(2);

        return captured.matches(this.getName());
    }

    public String getSourceLine()
    {
        return "const " + this.type + " " + this.getName() + " = " + this.getValue() + "; // Shader option " + this.getValue();
    }

    public boolean isUniform()
    {
        return this.isUniform && ShaderCurveBridge.areOptionCurvesEnabled();
    }

    @Override
    public String toString()
    {
        return "ShaderUniformConstOption{" + this.type + " " + this.name + ", uniform=" + this.isUniform + "}";
    }
}
