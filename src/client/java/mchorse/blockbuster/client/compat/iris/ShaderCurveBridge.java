package mchorse.blockbuster.client.compat.iris;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import mchorse.aperture.client.AsmShaderHandler;
import mchorse.aperture.utils.CodelineParser;
import mchorse.blockbuster.Blockbuster;

/**
 * The option-uniform machine behind Aperture's {@code shader_*} curves, on Iris
 * (S21 P218, part 2).
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/AsmShaderHandler.java}.
 * That class was named for its mechanism — it was a bag of static methods that
 * Aperture's {@code ShadersTransformer}, {@code ShaderPackParserTransformer},
 * {@code ShaderOptionVariableTransformer} and
 * {@code ShaderOptionVariableConstTransformer} spliced into Optifine bytecode.
 * None of that survives; the <i>state</i> and the <i>rules</i> do, unchanged,
 * and this class holds them. {@code mchorse.aperture.client.AsmShaderHandler}
 * remains as a thin facade over these statics so the curve classes still diff
 * 1:1 against {@code .tools/legacy-src}.</p>
 *
 * <h2>Legacy hook → modern seam</h2>
 *
 * <table border="1">
 *   <caption>Where each ASM hook went</caption>
 *   <tr><th>Legacy {@code AsmShaderHandler}</th><th>Port</th></tr>
 *   <tr><td>{@code getCachedShader} (reader interception)</td>
 *       <td>{@link #processSource(String)} from {@code JcppProcessorMixin} —
 *           Iris preprocesses every GLSL source through
 *           {@code JcppProcessor.glslPreprocessSource}, after include
 *           resolution, which is precisely where legacy sat.</td></tr>
 *   <tr><td>{@code setProgramUniform1i} / {@code 1f} (value override)</td>
 *       <td>{@link #pushBuiltinOverrides()} from {@code ProgramUniformsMixin} at
 *           the {@code TAIL} of {@code ProgramUniforms.update()} — <b>after</b>
 *           Iris has written its own value for the frame, so a curve wins while
 *           its channel is live and Iris' value stands the instant the channel
 *           empties. Ordering the other way round is the flicker the plan
 *           warns about.</td></tr>
 *   <tr><td>{@code updateOptionUniforms} (per-program option push)</td>
 *       <td>Iris custom uniforms: one {@code FloatCachedUniform} /
 *           {@code IntCachedUniform} per option, {@code PER_FRAME}, registered
 *           at pipeline-build time via {@code CustomUniformsBuilderMixin}. The
 *           supplier is {@link #optionUniformValue}, i.e. the curve value when
 *           one is written and the pack's own configured value otherwise —
 *           exactly legacy's
 *           {@code setProgramUniform*(uniform, parse(getShaderOption(id).getValue()))}
 *           followed by the curve override.</td></tr>
 *   <tr><td>{@code collectShaderOptions} / {@code getShaderOption} (demotion)</td>
 *       <td>{@link #beginOptionScan()} / {@link #scanLine(String, Collection)},
 *           driven by {@code IrisShaderOptions} while it walks the pack's
 *           sources at pack-load time.</td></tr>
 *   <tr><td>{@code afterInit} / {@code loadShaderPack} (cache reset)</td>
 *       <td>{@link #onPackLoadStart()} / {@link #onPackLoaded(String)} /
 *           {@link #reset()} from {@code IrisPackLifecycleMixin}.</td></tr>
 * </table>
 *
 * <h2>Pinned Iris internals</h2>
 *
 * <p>Every Iris type this phase binds is named in
 * {@code IrisMixinPlugin.PINNED_CLASSES} and asserted by
 * {@code ShaderCurveBridgeTest#irisInternalNamesArePinned}, so an Iris bump has
 * one place to update — the same discipline
 * {@code IrisPbrGifBridgeTest#irisInternalNamesArePinned} established in
 * P217.1. Unlike P217/P217.1 this phase compiles against Iris
 * ({@code modClientCompileOnly "maven.modrinth:iris:1.7.2+1.20.4"}); the
 * mixin-config plugin, not reflection, is what keeps a mismatched Iris from
 * crashing.</p>
 *
 * <h2>Headless by construction</h2>
 *
 * <p>Nothing in this class references an Iris or a GL type. The two places that
 * must are {@code IrisShaderOptions} (option discovery, custom-uniform
 * registration) and {@code ShaderCurveBridge.Sink} implementations; both are
 * behind {@link Sink}, so the whole machine is unit-testable with no Iris on
 * the classpath.</p>
 */
public final class ShaderCurveBridge
{
    /**
     * The uniform-name prefix. <b>Load-bearing across files</b>: a 1.12.2
     * camera profile stores option channels under {@code shader_<OPTION>} while
     * the GLSL uniform is {@code _uniform_<OPTION>}, and
     * {@code ShaderFloatOptionCurve}/{@code ShaderIntegerOptionCurve} bridge the
     * two by construction. Changing it silently breaks every existing profile.
     */
    public static final String UNIFORM_PREFIX = "_uniform_";

    /* Legacy AsmShaderHandler's five regexes, verbatim. */
    public static final Pattern PATTERN_DEFINE = Pattern.compile("^\\s*#define\\s", Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_IF = Pattern.compile("^\\s*#(?:el)?(?:if)\\s", Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONST = Pattern.compile("^\\s*const\\s");
    public static final Pattern PATTERN_CASE = Pattern.compile("^\\s*case\\s");
    public static final Pattern PATTERN_ARRAY = Pattern.compile("\\[\\s*[_A-Za-z].*\\s*\\]");

    /**
     * Curve-written integer uniform values, by uniform name.
     *
     * <p><b>This is the same map instance as
     * {@link mchorse.aperture.client.AsmShaderHandler#uniform1i}</b>, aliased at
     * the batch R merge step. The two halves of P218 were written in isolation
     * and each declared its own override map: the curve classes write the
     * facade's, the Iris push side reads this one. They must be one map or a
     * keyframe would be written where nothing reads it.</p>
     */
    public static final Map<String, Integer> uniform1i = AsmShaderHandler.uniform1i;

    /**
     * Curve-written float uniform values, by uniform name — the same instance as
     * {@link mchorse.aperture.client.AsmShaderHandler#uniform1f}; see
     * {@link #uniform1i}.
     */
    public static final Map<String, Float> uniform1f = AsmShaderHandler.uniform1f;

    /** Discovered float option uniforms, by <b>option</b> name (insertion order). */
    public static final Map<String, OptionUniform> option1f = new LinkedHashMap<>();

    /** Discovered integer option uniforms, by <b>option</b> name (insertion order). */
    public static final Map<String, OptionUniform> option1i = new LinkedHashMap<>();

    /**
     * Rewritten GLSL cache. Legacy keyed this by shader file path; the jcpp
     * seam hands us a source string with no path attached, so the key is the
     * source itself — same contract (never rewrite the same input twice, drop
     * everything on pack load), one fewer moving part.
     */
    public static final Map<String, String> cachedShaders = new HashMap<>();

    public static CodelineParser caseParser = new CodelineParser(':');
    public static CodelineParser constParser = new CodelineParser(';');

    /**
     * The pack's own {@code sunPathRotation}, captured at pack load so
     * {@code ShaderSunPathRotationCurve.reset()} can restore it. Legacy captured
     * it in {@code afterInit()} off {@code Shaders.sunPathRotation}.
     *
     * <p><b>Storage lives on the facade</b>
     * ({@link mchorse.aperture.client.AsmShaderHandler#sunPathRotation}) since
     * the batch R merge step — the curve's {@code reset()} restores from there,
     * so a second field here would restore a value nothing captured. Kept as an
     * accessor pair because the Iris mixins read and write it by this name.</p>
     */
    public static float sunPathRotation()
    {
        return AsmShaderHandler.sunPathRotation;
    }

    /** @see #sunPathRotation() */
    public static void sunPathRotation(float value)
    {
        AsmShaderHandler.sunPathRotation = value;
    }

    /**
     * The pack's configured value for every discovered option, by option name.
     * Captured at pack load (Iris' {@code OptionValues}), so the per-frame
     * supplier never touches Iris.
     */
    private static final Map<String, String> optionValues = new HashMap<>();

    /** Every option the pack exposes, in discovery order, by option name. */
    private static final Map<String, ShaderUniformOption> options = new LinkedHashMap<>();

    /** Every const option the pack exposes (only {@code sunPathRotation} is ever eligible). */
    private static final Map<String, ShaderUniformConstOption> constOptions = new LinkedHashMap<>();

    /** Name of the loaded pack, for {@link ShaderPackDenyList}. */
    private static String packName;

    /**
     * The {@code optifine.shader_option_curve} config row (owned by the curve
     * half of P218). Default {@code true} reproduces legacy's null-config
     * branch — {@code Aperture.optifineShaderOptionCurve == null ||
     * Aperture.optifineShaderOptionCurve.get()}.
     *
     * <p>Install at client init: {@code ShaderCurveBridge.optionCurvesEnabled =
     * () -> Aperture.optifineShaderOptionCurve.get();}</p>
     */
    public static BooleanSupplier optionCurvesEnabled = () -> true;

    /**
     * Re-run {@code CurveManager.refreshCurves()} after a pack (re)load, which
     * is what makes newly discovered {@code shader_<OPTION>} ids appear in the
     * curve editor. Legacy got this for free because Optifine's shader reload
     * re-entered Aperture's own refresh.
     *
     * <p>Install at client init:
     * {@code ShaderCurveBridge.curveRefresh = () -> ClientProxy.getCameraEditor().updateCurves();}
     * or whatever the curve half exposes.</p>
     */
    public static Runnable curveRefresh;

    /** The Iris-facing side, behind one interface — see {@link Sink}. */
    private static Sink sink = Sink.NONE;

    private ShaderCurveBridge()
    {}

    /* ------------------------------------------------------------------ */
    /* Sink                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Everything this bridge does to Iris/GL, behind one interface — the same
     * shape {@code IrisPbrGifBridge.Sink} uses, so the with-Iris path is
     * exercisable headlessly.
     */
    public interface Sink
    {
        Sink NONE = new Sink() {};

        /**
         * Write {@code value} to uniform {@code name} in the currently bound
         * program, if that program declares it. Implemented with
         * {@code glGetUniformLocation} + {@code glUniform1f}, cached per
         * program id.
         */
        default void pushFloat(String name, float value)
        {}

        /** @see #pushFloat(String, float) */
        default void pushInt(String name, int value)
        {}

        /** Drop cached uniform locations (a pack reload rebuilds every program). */
        default void invalidate()
        {}
    }

    public static void setSink(Sink value)
    {
        sink = value == null ? Sink.NONE : value;
    }

    public static Sink getSink()
    {
        return sink;
    }

    /* ------------------------------------------------------------------ */
    /* Config                                                              */
    /* ------------------------------------------------------------------ */

    public static boolean areOptionCurvesEnabled()
    {
        BooleanSupplier supplier = optionCurvesEnabled;

        return supplier == null || supplier.getAsBoolean();
    }

    /* ------------------------------------------------------------------ */
    /* Pack lifecycle                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Legacy {@code loadShaderPack()}: drop every cache before a pack is read.
     * Curve-written values survive — a curve that is mid-playback keeps driving
     * the new pack's uniform of the same name, which is what 1.12.2 did.
     */
    public static void onPackLoadStart()
    {
        cachedShaders.clear();
        option1f.clear();
        option1i.clear();
        options.clear();
        constOptions.clear();
        optionValues.clear();
        caseParser.reset();
        constParser.reset();
        packName = null;
        sink.invalidate();
    }

    /**
     * A pack finished loading: remember its name (for the deny-list) and ask
     * the curve half to rebuild its id table.
     */
    public static void onPackLoaded(String name)
    {
        packName = name;

        Runnable refresh = curveRefresh;

        if (refresh != null)
        {
            try
            {
                refresh.run();
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.error("Failed to refresh Aperture shader curves after a shader pack load", e);
            }
        }
    }

    /**
     * Shaders were switched off entirely. Everything the pack contributed goes,
     * including the curve-written values — with no program bound there is
     * nothing to un-stick, and leaving them would apply them to the next pack's
     * first frame.
     */
    public static void reset()
    {
        onPackLoadStart();
        uniform1f.clear();
        uniform1i.clear();
        sunPathRotation(0F);

        Runnable refresh = curveRefresh;

        if (refresh != null)
        {
            try
            {
                refresh.run();
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.error("Failed to refresh Aperture shader curves after shaders were disabled", e);
            }
        }
    }

    public static String getPackName()
    {
        return packName;
    }

    /* ------------------------------------------------------------------ */
    /* Option registration & discovery                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Register one pack option, translated from Iris' {@code StringOption} /
     * {@code BooleanOption} by {@code IrisShaderOptions}. {@code value} is the
     * pack's currently applied value; {@code values} its allowed set.
     */
    public static ShaderUniformOption addOption(ShaderUniformOption option, String currentValue)
    {
        options.put(option.getName(), option);

        if (currentValue != null)
        {
            optionValues.put(option.getName(), currentValue);
        }

        return option;
    }

    public static ShaderUniformConstOption addConstOption(ShaderUniformConstOption option, String currentValue)
    {
        constOptions.put(option.getName(), option);

        if (currentValue != null)
        {
            optionValues.put(option.getName(), currentValue);
        }

        return option;
    }

    public static Collection<ShaderUniformOption> getOptions()
    {
        return options.values();
    }

    public static Collection<ShaderUniformConstOption> getConstOptions()
    {
        return constOptions.values();
    }

    public static ShaderUniformOption getOption(String name)
    {
        return options.get(name);
    }

    /** Legacy {@code collectShaderOptions()}: reset the {@code case} accumulator. */
    public static void beginOptionScan()
    {
        caseParser.reset();
    }

    /**
     * Legacy {@code getShaderOption(line, path, option, mapOptions)} — the
     * demotion pass, run over every source line of the pack while options are
     * being collected. {@code option != null} in legacy meant "this line
     * <i>declares</i> an option", and declarations were never demotion sites;
     * the port's caller passes {@code false} for {@code declaresOption} on
     * every other line.
     */
    public static void scanLine(String line, Collection<ShaderUniformOption> candidates)
    {
        scanLine(line, candidates, false);
    }

    public static void scanLine(String line, Collection<ShaderUniformOption> candidates, boolean declaresOption)
    {
        if (declaresOption || !areOptionCurvesEnabled())
        {
            return;
        }

        if (PATTERN_IF.matcher(line).find())
        {
            for (ShaderUniformOption uniform : candidates)
            {
                if (uniform.isUniform())
                {
                    uniform.checkMacro(line);
                }
            }
        }
        else if (caseParser.cache.length() > 0 || PATTERN_CASE.matcher(line).find())
        {
            caseParser.parseLine(line);

            if (caseParser.isEnd)
            {
                String caseLine = caseParser.cache.toString();

                for (ShaderUniformOption uniform : candidates)
                {
                    if (uniform.isUniform())
                    {
                        uniform.checkCase(caseLine);
                    }
                }

                caseParser.reset();
            }
        }
        else if (PATTERN_ARRAY.matcher(line).find())
        {
            for (ShaderUniformOption uniform : candidates)
            {
                if (uniform.isUniform())
                {
                    uniform.checkArray(line);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Source rewriting                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * The {@code JcppProcessor.glslPreprocessSource} seam. Returns {@code source}
     * untouched when option curves are off, when no option is eligible, or when
     * anything at all goes wrong — a shader-curve feature must never be the
     * reason a pack fails to load.
     */
    public static String processSource(String source)
    {
        if (source == null || !areOptionCurvesEnabled() || (options.isEmpty() && constOptions.isEmpty()))
        {
            return source;
        }

        String cached = cachedShaders.get(source);

        if (cached != null)
        {
            return cached;
        }

        try
        {
            List<ShaderUniformOption> eligible = new ArrayList<>();
            List<ShaderUniformConstOption> eligibleConst = new ArrayList<>();

            for (ShaderUniformOption option : options.values())
            {
                if (!ShaderPackDenyList.isDenied(packName, option.getName()))
                {
                    eligible.add(option);
                }
            }

            for (ShaderUniformConstOption option : constOptions.values())
            {
                if (!ShaderPackDenyList.isDenied(packName, option.getName()))
                {
                    eligibleConst.add(option);
                }
            }

            String rewritten = ShaderSourceRewriter.rewrite(source, eligible, eligibleConst, ShaderCurveBridge::addOptionUniform, ShaderCurveBridge::addOptionUniformConst);

            cachedShaders.put(source, rewritten);

            return rewritten;
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.error("Failed to rewrite shader source for Aperture option curves; leaving it untouched", e);

            cachedShaders.put(source, source);

            return source;
        }
    }

    /** Legacy {@code addOptionUniform}. */
    public static void addOptionUniform(ShaderUniformOption option)
    {
        if (!option.isUniform())
        {
            return;
        }

        if (option.uniformType == ShaderUniformOption.INTEGER)
        {
            option1i.computeIfAbsent(option.getName(), name -> new OptionUniform(name, UNIFORM_PREFIX + name, true));
        }
        else if (option.uniformType == ShaderUniformOption.FLOAT)
        {
            option1f.computeIfAbsent(option.getName(), name -> new OptionUniform(name, UNIFORM_PREFIX + name, false));
        }
    }

    /**
     * Legacy {@code addOptionUniformConst}. Note the <b>bare</b> uniform name —
     * the const declaration is commented out by the rewriter, so no prefix is
     * needed and the shader body keeps reading {@code sunPathRotation}.
     */
    public static void addOptionUniformConst(ShaderUniformConstOption option)
    {
        if (!option.isUniform())
        {
            return;
        }

        if ("int".equals(option.type))
        {
            option1i.computeIfAbsent(option.getName(), name -> new OptionUniform(name, name, true));
        }
        else if ("float".equals(option.type))
        {
            option1f.computeIfAbsent(option.getName(), name -> new OptionUniform(name, name, false));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Per-frame push                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * The value an option uniform should carry this frame: the curve's value
     * when a channel wrote one, otherwise the pack's own configured value.
     * Legacy did the same in two steps ({@code updateOptionUniforms} pushed the
     * pack value, {@code setProgramUniform*} overrode it).
     */
    public static float optionUniformValue(OptionUniform uniform)
    {
        Float override = uniform1f.get(uniform.uniformName);

        if (override != null)
        {
            return override;
        }

        Integer intOverride = uniform1i.get(uniform.uniformName);

        if (intOverride != null)
        {
            return intOverride;
        }

        return parseFloat(optionValues.get(uniform.optionName));
    }

    /** @see #optionUniformValue(OptionUniform) */
    public static int optionUniformIntValue(OptionUniform uniform)
    {
        Integer override = uniform1i.get(uniform.uniformName);

        if (override != null)
        {
            return override;
        }

        Float floatOverride = uniform1f.get(uniform.uniformName);

        if (floatOverride != null)
        {
            return floatOverride.intValue();
        }

        return (int) parseFloat(optionValues.get(uniform.optionName));
    }

    private static float parseFloat(String value)
    {
        if (value == null)
        {
            return 0F;
        }

        try
        {
            return Float.parseFloat(value);
        }
        catch (NumberFormatException e)
        {
            return 0F;
        }
    }

    /**
     * Overwrite Iris' own value for every built-in uniform a curve is currently
     * driving. Called at the {@code TAIL} of {@code ProgramUniforms.update()},
     * so it runs after Iris wrote the frame's value and before the draw — the
     * ordering the plan flags as flicker-critical.
     *
     * <p>Only names with a live entry in {@link #uniform1f}/{@link #uniform1i}
     * are touched, and the curve classes remove their entry in {@code reset()},
     * so an emptied channel hands the uniform straight back to Iris on the very
     * next frame.</p>
     */
    public static void pushBuiltinOverrides()
    {
        if (uniform1f.isEmpty() && uniform1i.isEmpty())
        {
            return;
        }

        Sink target = sink;

        for (Map.Entry<String, Float> entry : uniform1f.entrySet())
        {
            target.pushFloat(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Integer> entry : uniform1i.entrySet())
        {
            target.pushInt(entry.getKey(), entry.getValue());
        }
    }

    /* ------------------------------------------------------------------ */
    /* The CurveManager seam                                               */
    /* ------------------------------------------------------------------ */

    /**
     * One discovered option uniform, as {@code CurveManager} needs to see it.
     *
     * @param id          the option name — the curve id is {@code "shader_" + id}
     * @param displayName the pack's label ({@code ShaderOption.getNameText()});
     *                    the option curves show {@code "<label>/<id>"} when it
     *                    differs from {@code id}
     * @param integer     {@code true} for {@code ShaderIntegerOptionCurve},
     *                    {@code false} for {@code ShaderFloatOptionCurve}
     * @param visible     legacy {@code ShaderOption.isVisible()} — whether the
     *                    pack lists the option in its own menu. Carried
     *                    explicitly (added at the batch R merge step) because
     *                    {@code CurveManager.getSortedOptions(visible, integer)}
     *                    re-derives the four registration groups by filtering on
     *                    it; without it every option would sort as one group and
     *                    the curve editor's row order would drift from 1.12.2.
     */
    public record ShaderOptionInfo(String id, String displayName, boolean integer, boolean visible)
    {}

    /**
     * Every discovered option uniform in <b>registration order</b>:
     * visible-float, visible-int, hidden-float, hidden-int, each group sorted by
     * display name, {@code sunPathRotation} excluded.
     *
     * <p>That is legacy {@code CurveManager.refreshCurves()}'s four
     * {@code getSortedOptions} calls, in that exact sequence — and since
     * {@code curves} is a {@code LinkedHashMap} whose iteration order is the
     * curve editor's row order, it is UI order too.</p>
     *
     * <p><b>Merge seam.</b> The curve half of P218 defines
     * {@code CurveManager.ShaderOptionSource} plus a static installer. This
     * method is the implementation; the merge step wires it with one line at
     * client init — see {@code plan/inbox/batchR-B.md}.</p>
     */
    public static List<ShaderOptionInfo> describeOptions()
    {
        List<ShaderOptionInfo> out = new ArrayList<>();

        collectSorted(out, option1f.values(), true, false);
        collectSorted(out, option1i.values(), true, true);
        collectSorted(out, option1f.values(), false, false);
        collectSorted(out, option1i.values(), false, true);

        return out;
    }

    private static void collectSorted(List<ShaderOptionInfo> out, Collection<OptionUniform> uniforms, boolean visible, boolean integer)
    {
        List<ShaderOptionInfo> group = new ArrayList<>();

        for (OptionUniform uniform : uniforms)
        {
            String id = uniform.optionName;

            if (ShaderUniformConstOption.SUN_PATH_ROTATION.equals(id))
            {
                continue;
            }

            ShaderUniformOption option = options.get(id);

            if (option == null || option.isVisible() != visible)
            {
                continue;
            }

            group.add(new ShaderOptionInfo(id, option.getNameText(), integer, visible));
        }

        group.sort((a, b) -> a.displayName().compareTo(b.displayName()));
        out.addAll(group);
    }

    /* ------------------------------------------------------------------ */

    /**
     * Replaces Optifine's {@code ShaderUniform1i}/{@code ShaderUniform1f}: the
     * pairing of a pack option with the GLSL uniform that now carries it.
     */
    public static final class OptionUniform
    {
        /** The pack's option name, e.g. {@code SHADOW_QUALITY}. */
        public final String optionName;

        /** The GLSL uniform name — {@code _uniform_SHADOW_QUALITY}, or the bare name for consts. */
        public final String uniformName;

        public final boolean integer;

        public OptionUniform(String optionName, String uniformName, boolean integer)
        {
            this.optionName = optionName;
            this.uniformName = uniformName;
            this.integer = integer;
        }

        @Override
        public String toString()
        {
            return "OptionUniform{" + this.optionName + " -> " + this.uniformName + (this.integer ? ", int}" : ", float}");
        }
    }
}
