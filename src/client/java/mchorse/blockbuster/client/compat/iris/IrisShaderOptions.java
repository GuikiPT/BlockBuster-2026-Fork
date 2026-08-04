package mchorse.blockbuster.client.compat.iris;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.compat.iris.ShaderCurveBridge.OptionUniform;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.LanguageMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.FileNode;
import net.irisshaders.iris.shaderpack.option.MergedStringOption;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.StringOption;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElementScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform;

/**
 * The only class in the port that names Iris types for P218 (besides the
 * mixins). Everything here is called <b>from</b> a mixin that
 * {@code IrisMixinPlugin} already refused to apply unless Iris is present and
 * matches, so this class is never loaded on a vanilla install and never on the
 * test classpath.
 *
 * <p>Two jobs:</p>
 *
 * <h2>Two phases, and why (S22 P269)</h2>
 *
 * <p>Discovery used to be a single call at {@code Iris.loadShaderpack}
 * {@code @RETURN}. That is <b>after</b> Iris has already preprocessed every
 * program source, so the GLSL rewriter saw an empty option set and rewrote
 * nothing — see {@code ShaderPackOptionsMixin} for the bytecode-level ordering.
 * It is split in two now:</p>
 *
 * <ul>
 *   <li>{@link #captureOptions(ShaderPackOptions)} — from
 *       {@code ShaderPackOptionsMixin}, at {@code ShaderPackOptions.<init>}
 *       {@code @RETURN}: translate the options and run the demotion pass, before
 *       a single source is read. This is legacy's
 *       {@code collectShaderOptions} position.</li>
 *   <li>{@link #refineOptionMetadata()} — from {@code IrisPackLifecycleMixin}, at
 *       {@code loadShaderpack} {@code @RETURN}: attach the display names and the
 *       visibility flags, which live on the {@code ShaderPack} and do not exist
 *       yet in phase one. They are read only by {@code describeOptions()}, which
 *       runs later still (the curve refresh), so nothing is lost by deferring
 *       them.</li>
 * </ul>
 *
 * <ol>
 *   <li><b>Option discovery</b> ({@link #captureOptions(ShaderPackOptions)}) — Optifine handed
 *       Aperture a {@code Map<String, ShaderOption>} mid-parse and let it
 *       substitute its own subclass. Iris parses options into
 *       {@code OptionSet}/{@code ShaderPackOptions} and there is nothing to
 *       substitute, so the port <i>translates</i>: every {@code StringOption}
 *       becomes a {@link ShaderUniformOption} carrying the same name,
 *       description, current value and allowed-value set, which is all the
 *       eligibility rules ever read. Boolean options are skipped — they were
 *       Optifine {@code ShaderOptionSwitch}es, never uniform candidates.</li>
 *   <li><b>Custom uniforms</b> ({@link #addUniforms(List)}) — one
 *       {@code FloatCachedUniform}/{@code IntCachedUniform} per discovered
 *       option, {@code PER_FRAME}, appended to the pipeline's custom-uniform
 *       list at build time. This replaces legacy's per-program
 *       {@code updateOptionUniforms()} push.</li>
 * </ol>
 *
 * <p>Visibility mirrors the option menu: an option that appears on a screen in
 * {@code OptionMenuContainer} is visible, everything else is hidden — Optifine's
 * {@code ShaderOption.isVisible()}, which is what splits
 * {@code CurveManager}'s registration into visible-then-hidden groups. Display
 * names come from the pack language map's {@code option.<name>} key, exactly
 * like {@code ShaderOption.getNameText()}.</p>
 */
public final class IrisShaderOptions
{
    private IrisShaderOptions()
    {}

    /**
     * <b>Phase one</b> — read the pack's options into {@link ShaderCurveBridge}
     * and run the demotion pass, from {@code ShaderPackOptions.<init>}
     * {@code @RETURN}, i.e. before Iris reads the first program source.
     *
     * <p>Total: any failure leaves the bridge with whatever it had (i.e. nothing)
     * and logs, so shader curves silently disappear rather than breaking pack
     * loading.</p>
     *
     * <p>Display name and visibility are deliberately left at
     * {@link ShaderUniformOption}'s own defaults here — the identifier, and
     * legacy's "more than one allowed value ⇒ visible" rule — and are replaced by
     * {@link #refineOptionMetadata()}. Neither the pack's language map nor its
     * option menu exists this early, and neither value is read before the curve
     * refresh, which runs after phase two.</p>
     */
    public static void captureOptions(ShaderPackOptions options)
    {
        try
        {
            if (options == null)
            {
                return;
            }

            OptionSet set = options.getOptionSet();
            OptionValues values = options.getOptionValues();

            for (Map.Entry<String, MergedStringOption> entry : set.getStringOptions().entrySet())
            {
                StringOption option = entry.getValue().getOption();
                String name = option.getName();
                ImmutableList<String> allowed = option.getAllowedValues();
                List<String> all = new ArrayList<>(allowed);

                if (all.isEmpty())
                {
                    all.add(option.getDefaultValue());
                }

                String applied = values.getStringValue(name).orElse(option.getDefaultValue());

                if (ShaderUniformConstOption.SUN_PATH_ROTATION.equals(name))
                {
                    /* sunPathRotation is a const directive, not a #define; it
                     * takes the const arm so ShaderSunPathRotationCurve can
                     * drive it. */
                    ShaderUniformConstOption constOption = new ShaderUniformConstOption(name, "float", option.getComment().orElse(null), applied, all.toArray(new String[0]), null);

                    ShaderCurveBridge.addConstOption(constOption, applied);

                    continue;
                }

                ShaderUniformOption translated = new ShaderUniformOption(name, option.getComment().orElse(null), applied, all.toArray(new String[0]), null);

                ShaderCurveBridge.addOption(translated, applied);
            }

            /* The pack's own sunPathRotation is captured by
             * PackDirectivesMixin, not here: on Iris it is a pack *directive*
             * (PackDirectives.getSunPathRotation()) rather than an option
             * value, and the directive getter is the only place it is
             * authoritative. */

            scanIncludeGraph(options);
        }
        catch (Exception | LinkageError e)
        {
            Blockbuster.LOGGER.error("Failed to read Iris shader-pack options for Aperture curves", e);
        }
    }

    /**
     * <b>Phase two</b> — attach each discovered option's display name and
     * visibility, from {@code Iris.loadShaderpack} {@code @RETURN}.
     *
     * <p>Both come off the finished {@code ShaderPack}: names from the pack
     * language map's {@code option.<name>} key (Optifine
     * {@code ShaderOption.getNameText()}), visibility from
     * {@code OptionMenuContainer} (Optifine {@code ShaderOption.isVisible()},
     * which is what splits {@code CurveManager}'s registration into
     * visible-then-hidden groups). Neither exists while
     * {@link #captureOptions(ShaderPackOptions)} runs, and neither is read until
     * {@code describeOptions()} — which runs after this, on the same call.</p>
     *
     * <p>Total, like phase one: on failure the options keep their identifiers as
     * labels and their default visibility, which is degraded row order and
     * labelling rather than a missing feature.</p>
     */
    public static void refineOptionMetadata()
    {
        try
        {
            Optional<ShaderPack> current = Iris.getCurrentPack();

            if (current.isEmpty())
            {
                return;
            }

            ShaderPack pack = current.get();
            Map<String, String> names = languageNames(pack);
            Set<String> visible = visibleOptionIds(pack.getMenuContainer());

            for (ShaderUniformOption option : ShaderCurveBridge.getOptions())
            {
                String name = option.getName();

                option.setNameText(names.getOrDefault("option." + name, name));
                option.setVisible(visible.contains(name));
            }

            for (ShaderUniformConstOption option : ShaderCurveBridge.getConstOptions())
            {
                String name = option.getName();

                option.setNameText(names.getOrDefault("option." + name, name));
            }
        }
        catch (Exception | LinkageError e)
        {
            Blockbuster.LOGGER.error("Failed to read Iris shader-pack option labels for Aperture curves", e);
        }
    }

    /**
     * The demotion pass, over the pack's <b>whole</b> include graph.
     *
     * <p>This has to happen before a single source is rewritten, and it has to
     * see every file — an option demoted by an {@code #if} in
     * {@code composite.fsh} must not be rewritten in {@code gbuffers_terrain.fsh}.
     * Optifine gave Aperture that scope for free (its
     * {@code ShaderPackParser.collectShaderOptions} walked the pack and called
     * the hook per line). Iris' equivalent is
     * {@code ShaderPackOptions.getIncludes()}: an {@code IncludeGraph} whose
     * {@code FileNode}s hold the raw lines of every file the pack references.
     *
     * <p><b>S22 P269.</b> "Before a single source is rewritten" is why this runs
     * from {@code ShaderPackOptionsMixin} and not from the pack-lifecycle
     * {@code @RETURN} it used to: Iris builds its {@code ProgramSet}s — and
     * therefore preprocesses every source through {@code JcppProcessor} — inside
     * the {@code ShaderPack} constructor, well before {@code loadShaderpack}
     * returns.</p>
     */
    private static void scanIncludeGraph(ShaderPackOptions options)
    {
        try
        {
            for (FileNode node : options.getIncludes().getNodes().values())
            {
                ShaderCurveBridge.beginOptionScan();

                for (String line : node.getLines())
                {
                    ShaderCurveBridge.scanLine(line, ShaderCurveBridge.getOptions());
                }
            }
        }
        catch (Exception | LinkageError e)
        {
            Blockbuster.LOGGER.error("Failed to scan the Iris include graph for Aperture option demotion", e);
        }
    }

    /**
     * Append one custom uniform per discovered option to the pipeline's
     * uniform list. {@code UniformUpdateFrequency.PER_FRAME} is the right
     * frequency: a curve is sampled once per rendered frame from the camera
     * runner, and {@code PER_TICK} would quantise keyframes to 20 Hz while
     * {@code CUSTOM} would need a notifier we have no reason to own.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addUniforms(List list)
    {
        try
        {
            for (OptionUniform uniform : ShaderCurveBridge.option1f.values())
            {
                list.add(new FloatCachedUniform(uniform.uniformName, UniformUpdateFrequency.PER_FRAME, () -> ShaderCurveBridge.optionUniformValue(uniform)));
            }

            for (OptionUniform uniform : ShaderCurveBridge.option1i.values())
            {
                list.add(new IntCachedUniform(uniform.uniformName, UniformUpdateFrequency.PER_FRAME, () -> ShaderCurveBridge.optionUniformIntValue(uniform)));
            }
        }
        catch (Exception | LinkageError e)
        {
            Blockbuster.LOGGER.error("Failed to register Aperture option uniforms with Iris", e);
        }
    }

    /** The loaded pack's name, for {@link ShaderPackDenyList}. */
    public static String currentPackName()
    {
        try
        {
            return Iris.getCurrentPackName();
        }
        catch (Exception | LinkageError e)
        {
            return null;
        }
    }

    private static Map<String, String> languageNames(ShaderPack pack)
    {
        Map<String, String> out = new HashMap<>();

        try
        {
            LanguageMap map = pack.getLanguageMap();
            Map<String, String> fallback = map.getTranslations("en_us");

            if (fallback != null)
            {
                out.putAll(fallback);
            }
        }
        catch (Exception | LinkageError e)
        {
            /* Display names fall back to the identifier — Optifine's own
             * behaviour when a pack ships no lang file. */
        }

        return out;
    }

    private static Set<String> visibleOptionIds(OptionMenuContainer container)
    {
        Set<String> out = new HashSet<>();

        if (container == null)
        {
            return out;
        }

        collectVisible(out, container, container.mainScreen, new HashSet<>());

        return out;
    }

    private static void collectVisible(Set<String> out, OptionMenuContainer container, OptionMenuElementScreen screen, Set<String> seen)
    {
        if (screen == null)
        {
            return;
        }

        for (OptionMenuElement element : screen.elements)
        {
            if (element instanceof OptionMenuOptionElement option)
            {
                out.add(option.optionId);
            }
            else if (element instanceof OptionMenuLinkElement link && seen.add(link.targetScreenId))
            {
                collectVisible(out, container, container.subScreens.get(link.targetScreenId), seen);
            }
        }
    }
}
