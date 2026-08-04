package mchorse.blockbuster.client.compat.iris;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Iris shader-pack compatibility probe (roadmap P217).
 *
 * <p>The 1.20.4 stand-in for everything Optifine's
 * {@code net.optifine.shaders.Shaders} used to answer. Two questions matter to
 * the port: <b>is a shader pack in use</b> (Aperture's {@code isShaderLoaded},
 * wired in P218.1) and <b>is Iris rendering the shadow map right now</b> (the
 * shadow-pass guards that keep GUI-only/gizmo geometry out of the shadow
 * buffer). Both come off Iris' deliberately-stable v0 API,
 * {@code net.irisshaders.iris.api.v0.IrisApi}.</p>
 *
 * <h2>Why reflection and not <code>modCompileOnly</code></h2>
 *
 * <p>The plan sketch proposed {@code modCompileOnly "maven.modrinth:iris:…"},
 * the shape BBS uses. This port reflects instead, deliberately:</p>
 *
 * <ul>
 *   <li><b>The legacy contract is reflection.</b> {@code OptifineHelper} looks
 *       a class up once, caches the lookup (including its failure), swallows
 *       every exception and falls back to {@code false}. Reflecting keeps that
 *       contract literally rather than re-expressing it, so the helper still
 *       diffs 1:1 against {@code .tools/legacy-src}.</li>
 *   <li><b>It degrades where a compile-time dep would crash.</b> A hard
 *       reference to {@code IrisApi} binds one method descriptor at compile
 *       time; a user running an Iris build that moved it gets
 *       {@code NoSuchMethodError} mid-frame. Reflection returns {@code false}
 *       and the frame renders as vanilla — which is exactly the stated
 *       requirement ("a broken Iris version must degrade to vanilla
 *       rendering, never crash").</li>
 *   <li><b>It keeps the build self-contained.</b> The project resolves only
 *       Fabric, vecmath and JUnit today; {@code modCompileOnly} would add a
 *       Modrinth-maven fetch + Loom remap to every build of the mod, for a
 *       post-parity optional integration. The v0 API is versioned precisely so
 *       that a two-method reflective bind stays valid.</li>
 * </ul>
 *
 * <h2>Caching contract</h2>
 *
 * <p>Legacy's "{@code checked}/{@code element}" pair is load-bearing: the
 * <i>lookup</i> happens once per JVM and is never retried, while the
 * <i>state</i> is read live on every call. A shader pack enabled mid-session
 * therefore still reports correctly (the class was always there; only the flag
 * changed), and a session that started without Iris never pays for repeated
 * failed lookups. {@link #instance} caches the API singleton for the same
 * reason — it is a singleton by contract — but
 * {@code isRenderingShadowPass()} is invoked afresh every time.</p>
 *
 * <p>Iris presence is gated on {@code FabricLoader.isModLoaded("iris")} first
 * so that a vanilla install costs one boolean read per probe and never touches
 * {@code Class.forName}.</p>
 *
 * @see mchorse.mclib.utils.OptifineHelper
 */
public final class IrisCompat
{
    /** Iris' Fabric mod id. */
    public static final String MOD_ID = "iris";

    /** Iris' stable public API entry point. */
    public static final String API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    /**
     * Test seam: when non-null this replaces the whole live probe, so render
     * gating can be exercised headlessly by flipping a supplier. Mirrors the
     * {@code MorphTracker.shadowPass} seam style used elsewhere in the port.
     */
    public static BooleanSupplier shadowPassOverride;

    /** Test seam for {@link #isShaderPackInUse()}. */
    public static BooleanSupplier shaderPackOverride;

    /** Test seam for {@link #isLoaded()}. */
    public static BooleanSupplier loadedOverride;

    /* One-shot lookups (legacy's ReflectionElement.checked/element pairs). */
    private static boolean loadedChecked;
    private static boolean loaded;

    private static boolean apiChecked;
    private static Object instance;
    private static Method shadowPass;
    private static Method shaderPackInUse;

    private IrisCompat()
    {}

    /**
     * Whether Iris is installed at all. One-shot; a mod cannot appear mid-run.
     */
    public static boolean isLoaded()
    {
        if (loadedOverride != null)
        {
            return loadedOverride.getAsBoolean();
        }

        if (!loadedChecked)
        {
            try
            {
                loaded = FabricLoader.getInstance().isModLoaded(MOD_ID);
            }
            catch (Throwable t)
            {
                /* No loader (plain-JVM harness) — behave as "absent". */
            }

            loadedChecked = true;
        }

        return loaded;
    }

    /**
     * Whether Iris is drawing the shadow map on this pass. The modern
     * {@code net.optifine.shaders.Shaders.isShadowPass}.
     *
     * <p>Returns {@code false} whenever Iris is absent, broken, or the probe
     * throws — "render as normal" is always the safe default, since gating a
     * parity feature on Iris presence would make the port behave differently
     * from 1.12.2 on a vanilla install.</p>
     */
    public static boolean isShadowPass()
    {
        if (shadowPassOverride != null)
        {
            return shadowPassOverride.getAsBoolean();
        }

        return invoke(shadowPass());
    }

    /**
     * Whether a shader pack is currently enabled — the modern
     * {@code Shaders.shaderPackLoaded}. Consumed by Aperture's own
     * {@code OptifineHelper.isShaderLoaded()} in P218.1.
     */
    public static boolean isShaderPackInUse()
    {
        if (shaderPackOverride != null)
        {
            return shaderPackOverride.getAsBoolean();
        }

        return invoke(shaderPackInUse());
    }

    /**
     * Drop every cached lookup and test seam. Test-only — production code has
     * no reason to re-probe, and doing so would break the one-shot contract.
     */
    public static void reset()
    {
        shadowPassOverride = null;
        shaderPackOverride = null;
        loadedOverride = null;

        loadedChecked = false;
        loaded = false;

        apiChecked = false;
        instance = null;
        shadowPass = null;
        shaderPackInUse = null;
    }

    /* --------------------------------------------------------------------- */
    /* Reflection                                                            */
    /* --------------------------------------------------------------------- */

    private static Method shadowPass()
    {
        bindApi();

        return shadowPass;
    }

    private static Method shaderPackInUse()
    {
        bindApi();

        return shaderPackInUse;
    }

    /**
     * Legacy {@code findShadersClass()}: resolve once, never retry, swallow
     * everything. A failure leaves the methods null and every probe answers
     * {@code false} for the rest of the session.
     */
    private static void bindApi()
    {
        if (apiChecked)
        {
            return;
        }

        apiChecked = true;

        if (!isLoaded())
        {
            return;
        }

        try
        {
            Class<?> api = Class.forName(API_CLASS);

            instance = api.getMethod("getInstance").invoke(null);

            if (instance != null)
            {
                shadowPass = api.getMethod("isRenderingShadowPass");
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            }
        }
        catch (Throwable t)
        {
            instance = null;
            shadowPass = null;
            shaderPackInUse = null;
        }
    }

    /** Live state read — never cached, per the legacy caching contract. */
    private static boolean invoke(Method method)
    {
        if (method == null || instance == null)
        {
            return false;
        }

        try
        {
            return (Boolean) method.invoke(instance);
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}
