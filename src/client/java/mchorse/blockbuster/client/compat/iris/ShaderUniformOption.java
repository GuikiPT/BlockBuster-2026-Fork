package mchorse.blockbuster.client.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A shader-pack {@code #define} option that Aperture can turn into a live
 * uniform (S21 P218).
 *
 * <p>Legacy source:
 * {@code AsmShaderHandler$ShaderUniformOption}, which extended Optifine's
 * {@code net.optifine.shaders.config.ShaderOptionVariable} and was substituted
 * for it by {@code ShaderOptionVariableTransformer} at
 * {@code ShaderOptionVariable.parseOption} time. There is no Optifine base
 * class on 1.20.4, so the two inherited behaviours P218 actually depends on are
 * inlined here verbatim:</p>
 *
 * <ul>
 *   <li>{@code ShaderOptionVariable.PATTERN_VARIABLE} and its
 *       {@code matchesLine} (including the legacy quirk that the captured name
 *       is compared with {@link String#matches(String)} — a <i>regex</i> match
 *       against the option name, not an equality test);</li>
 *   <li>{@code ShaderOptionVariable}'s non-uniform {@code getSourceLine()}
 *       ({@code "#define NAME VALUE // Shader option VALUE"}) and its
 *       constructor's {@code setVisible(getValues().length > 1)} — visibility
 *       is what splits {@code CurveManager}'s visible-then-hidden registration
 *       order.</li>
 * </ul>
 *
 * <h2>Eligibility, ported verbatim</h2>
 *
 * <p>{@link #uniformType} is one of {@link #NOT_SUPPORT}, {@link #INTEGER},
 * {@link #FLOAT}. An option is {@code INTEGER} when <b>every</b> allowed value
 * parses as an {@code int}, else {@code FLOAT} when every allowed value parses
 * as a {@code float}, else {@code NOT_SUPPORT}. A {@code null} value, or a name
 * containing {@code "__"} or starting with {@code "gl_"} (case-insensitively),
 * is {@code NOT_SUPPORT} outright — those are reserved by the GLSL spec, and a
 * {@code #define} that expands to one cannot legally become a uniform.</p>
 *
 * <p>Three <i>demotions</i> run during option discovery and can only ever move
 * an option to {@code NOT_SUPPORT}: {@link #checkMacro(String)} (the option is
 * read by {@code #if}/{@code #elif}, so the preprocessor must still see a
 * literal), {@link #checkCase(String)} (it is a {@code case} label, which must
 * be a constant expression) and {@link #checkArray(String)} (it sizes an array).
 * All three are the reason the rewriter may safely replace the remaining
 * {@code #define}s with uniform references.</p>
 *
 * <h2>The {@code doPatch} latch</h2>
 *
 * <p>{@link #doPatch} is a global latch, static exactly as in legacy. While it
 * is {@code false}, a uniform-eligible option reports {@code matchesLine} =
 * {@code false} so that <i>nothing except</i> the rewrite pass can match (in
 * legacy, Optifine itself called {@code matchesLine} when applying option
 * values to sources, and matching there would have written the literal back).
 * {@link ShaderSourceRewriter} raises it for the duration of one rewrite and
 * always lowers it in a {@code finally}.</p>
 */
public class ShaderUniformOption
{
    public static final int NOT_SUPPORT = 0;
    public static final int INTEGER = 1;
    public static final int FLOAT = 2;

    /** Optifine's {@code ShaderOptionVariable.PATTERN_VARIABLE}, verbatim. */
    public static final Pattern PATTERN_VARIABLE = Pattern.compile("^\\s*#define\\s+(\\w+)\\s+(-?[0-9\\.Ff]+|\\w+)\\s*(//.*)?$");

    /** @see ShaderUniformOption the {@code doPatch} latch */
    public static boolean doPatch = false;

    public final String name;
    public final String description;
    public final String[] values;
    public final String path;

    public final Pattern defineChecker;
    public final Pattern caseChecker;
    public final Pattern arrayChecker;

    public String value;
    public int uniformType;

    private boolean enabled = true;
    private boolean visible;
    private String nameText;

    public ShaderUniformOption(String name, String description, String value, String[] values, String path)
    {
        this.name = name;
        this.description = description;
        this.value = value;
        this.values = values == null ? new String[] {value} : values;
        this.path = path;
        this.nameText = name;
        this.visible = this.values.length > 1;

        this.defineChecker = Pattern.compile(String.format(".*\\W%s(?:\\W.*)?", name));
        this.caseChecker = Pattern.compile(String.format("^\\s*case\\s+%s\\s*:", name));
        this.arrayChecker = Pattern.compile(String.format("\\[(?:.*\\W)?%s(?:\\W.*)?\\]", name));

        if (value != null && !this.checkReversedName(name))
        {
            boolean isInteger = true;
            boolean isFloat = true;

            for (String val : this.values)
            {
                isInteger = isInteger && this.checkInt(val);
                isFloat = isFloat && this.checkFloat(val);
            }

            this.uniformType = isInteger ? INTEGER : (isFloat ? FLOAT : NOT_SUPPORT);
        }
        else
        {
            this.uniformType = NOT_SUPPORT;
        }
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

    /**
     * The pack's human-readable label for this option ({@code option.<name>} in
     * the pack's language map), defaulting to the identifier — Optifine's
     * {@code ShaderOption.getNameText()}. {@code CurveManager} sorts the visible
     * group by this string and {@code ShaderFloatOptionCurve} shows
     * {@code "<label>/<id>"} when it differs from the id.
     */
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

    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }

    /**
     * Legacy {@code ShaderOptionVariable.matchesLine} with the
     * {@link #doPatch} guard in front of it.
     */
    public boolean matchesLine(String line)
    {
        if (this.isUniform() && !doPatch)
        {
            return false;
        }

        Matcher matcher = PATTERN_VARIABLE.matcher(line);

        if (!matcher.matches())
        {
            return false;
        }

        String captured = matcher.group(1);

        return captured.matches(this.getName());
    }

    public String getSourceLine()
    {
        if (this.isUniform())
        {
            return "#define " + this.getName() + " " + ShaderCurveBridge.UNIFORM_PREFIX + this.getName();
        }

        return "#define " + this.getName() + " " + this.getValue() + " // Shader option " + this.getValue();
    }

    /**
     * Legacy consulted {@code Aperture.optifineShaderOptionCurve} here and
     * treated a {@code null} config (too early in startup) as "on"; the port's
     * equivalent is {@link ShaderCurveBridge#optionCurvesEnabled}, whose default
     * is the same permissive {@code true}.
     */
    public boolean isUniform()
    {
        return this.uniformType != NOT_SUPPORT && ShaderCurveBridge.areOptionCurvesEnabled();
    }

    public void checkMacro(String line)
    {
        if (this.defineChecker.matcher(line).matches())
        {
            this.uniformType = NOT_SUPPORT;
        }
    }

    public void checkCase(String line)
    {
        if (this.caseChecker.matcher(line).find())
        {
            this.uniformType = NOT_SUPPORT;
        }
    }

    public void checkArray(String line)
    {
        if (this.arrayChecker.matcher(line).find())
        {
            this.uniformType = NOT_SUPPORT;
        }
    }

    private boolean checkInt(String str)
    {
        try
        {
            Integer.parseInt(str);

            return true;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    private boolean checkFloat(String str)
    {
        try
        {
            Float.parseFloat(str);

            return true;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    private boolean checkReversedName(String name)
    {
        return name == null || name.contains("__") || name.toLowerCase().startsWith("gl_");
    }

    @Override
    public String toString()
    {
        return "ShaderUniformOption{" + this.name + ", type=" + this.uniformType + "}";
    }
}
