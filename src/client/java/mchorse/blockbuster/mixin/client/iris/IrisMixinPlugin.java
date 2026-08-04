package mchorse.blockbuster.mixin.client.iris;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Version gate for every Iris mixin in {@code blockbuster.iris.mixins.json}
 * (S21 P218).
 *
 * <p>P218 is the first phase in this port that compiles against another mod's
 * internals. {@code net.irisshaders.iris.shaderpack.*},
 * {@code net.irisshaders.iris.uniforms.custom.*} and
 * {@code net.irisshaders.iris.gl.program.*} carry no stability guarantee — the
 * package root itself moved from {@code net.coderbot.iris} within the 1.20
 * line. So the build pins exactly one Iris
 * ({@code maven.modrinth:iris:1.7.2+1.20.4}) and this plugin refuses to apply a
 * single mixin unless <b>every</b> pinned class is actually present.</p>
 *
 * <p>The failure mode is deliberate and matches legacy's: Aperture's
 * {@code AsmShaderHandler.callResolveIncludes} caught
 * {@code NoSuchMethodException} and logged
 * {@code "Do not support this version of Optifine!"}, leaving the game running
 * without shader curves. Here the equivalent is one warning at mixin-config
 * load and a mod that behaves exactly as it did before P218 — Iris renders, the
 * {@code shader_*} channels in a profile still round-trip losslessly (S15 P180
 * total readers), they just do not drive anything.</p>
 *
 * <p>Note the check is <i>presence</i>, not a version string comparison. A fork
 * or a backport that keeps the same internals is welcome to work, and a version
 * that renamed them is caught whatever it calls itself. {@link #IRIS_VERSION}
 * records what was tested and is what the report/inbox quotes.</p>
 *
 * <h2>The probe must not <i>load</i> anything (S22 batch V-E, 2026-07-26)</h2>
 *
 * <p>This gate originally probed with
 * {@code Class.forName(name, false, loader)}, and that silently killed the whole
 * config in game. {@code Class.forName} with {@code initialize = false} still
 * <b>defines</b> the class in the classloader, and Fabric's class tracker is
 * exactly {@code findLoadedClass(name) != null}
 * ({@code KnotClassDelegate.isClassLoaded}). Mixin builds each {@code MixinInfo}
 * <i>after</i> calling this plugin's {@link #onLoad(String)} and, for every
 * declared target, does:</p>
 *
 * <pre>
 * if (tracker.isClassLoaded(target) &amp;&amp; !isReloading())
 *     "Critical problem: %s target %s was loaded too early."
 * </pre>
 *
 * <p>({@code org.spongepowered.asm.mixin.transformer.MixinInfo}, mixin 0.8.7.)
 * Six of the (then) ten {@link #PINNED_CLASSES} <i>are</i> the mixin targets, so
 * the gate loaded every class it was about to guard and all six mixins were
 * refused — the user's log carries one such line per mixin, at mixin-prepare
 * time, on every session with Iris installed. Note the tracker check runs
 * <b>before</b> {@link #shouldApplyMixin}, so answering {@code false} here does
 * not suppress it either; the only fix is to never define the class.</p>
 *
 * <p>{@link #isPresent} therefore probes the <i>resource</i>
 * ({@code net/irisshaders/iris/Iris.class}), which reads the mod jar's zip entry
 * and defines nothing. Knot's {@code getResource} forwards straight to the
 * {@code URLClassLoader} holding every mod jar, and mod jars are on it before
 * mixin bootstrap (the configs themselves come from them), so this sees exactly
 * what {@code Class.forName} saw.</p>
 */
public class IrisMixinPlugin implements IMixinConfigPlugin
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster/iris");

    /** Iris' Fabric mod id. */
    public static final String MOD_ID = "iris";

    /**
     * The single Iris build P218 is compiled and eyeball-tested against —
     * `maven.modrinth:iris:1.7.0+1.20.1`, file `iris-1.7.0+mc1.20.1.jar`. Kept
     * in sync with `iris_version` in gradle.properties by
     * {@code ShaderCurveBridgeTest}.
     */
    public static final String IRIS_VERSION = "1.7.0+1.20.1";

    /**
     * Every Iris internal the mixins in this config bind. If any one of them
     * cannot be loaded, none of the mixins apply.
     *
     * <p>This is the one place to update on an Iris bump — the same
     * single-point-of-truth discipline as
     * {@code IrisPbrGifBridgeTest#irisInternalNamesArePinned} (P217.1).</p>
     */
    public static final String[] PINNED_CLASSES =
    {
        "net.irisshaders.iris.Iris",
        "net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor",
        "net.irisshaders.iris.shaderpack.option.ShaderPackOptions",
        "net.irisshaders.iris.shaderpack.properties.PackDirectives",
        "net.irisshaders.iris.gl.program.ProgramUniforms",
        "net.irisshaders.iris.uniforms.custom.CustomUniforms",
        "net.irisshaders.iris.uniforms.custom.CustomUniforms$Builder",
        "net.irisshaders.iris.uniforms.custom.cached.CachedUniform",
        "net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform",
        "net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform",
        "net.irisshaders.iris.gl.uniform.UniformUpdateFrequency"
    };

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage)
    {
        this.enabled = check();
    }

    /**
     * Iris present <i>and</i> every pinned internal resolvable. Public and
     * static so it can be asserted headlessly (where it always answers
     * {@code false} — Iris is {@code modClientCompileOnly}, never on the test
     * classpath).
     */
    public static boolean check()
    {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID))
        {
            return false;
        }

        return allPinnedPresent(IrisMixinPlugin.class.getClassLoader());
    }

    /**
     * The internals half of {@link #check()}, against an explicit loader.
     *
     * <p>Headless-test seam: the mod-presence half needs a Fabric runtime and
     * always answers {@code false} in the suite, so this is the only way to
     * exercise the pinned-class walk — and, more importantly, to prove it never
     * asks the loader to <i>load</i> anything.</p>
     */
    public static boolean allPinnedPresent(ClassLoader loader)
    {
        for (String name : PINNED_CLASSES)
        {
            if (!isPresent(loader, name))
            {
                LOGGER.error("Iris is installed but '{}' is missing — Aperture's shader-bound curves are disabled. This build of Blockbuster targets Iris {}.", name, IRIS_VERSION);

                return false;
            }
        }

        return true;
    }

    /**
     * Is {@code name} on {@code loader}, <b>without loading it</b>?
     *
     * <p>See the class javadoc: defining a mixin target before Mixin reads the
     * config is what disabled all six mixins. A resource lookup answers the same
     * question from the jar's zip directory and leaves
     * {@code findLoadedClass(name)} null, which is what Fabric's class tracker
     * reports to Mixin.</p>
     *
     * <p>{@code Class$Inner} needs no special casing — {@code '$'} survives the
     * dot-to-slash rewrite and is literally how the entry is named.</p>
     */
    public static boolean isPresent(ClassLoader loader, String name)
    {
        if (loader == null)
        {
            return false;
        }

        try
        {
            return loader.getResource(name.replace('.', '/') + ".class") != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        return this.enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {}

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}
}
