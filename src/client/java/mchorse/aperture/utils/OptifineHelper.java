package mchorse.aperture.utils;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import mchorse.blockbuster.client.compat.iris.IrisCompat;
import net.minecraft.client.MinecraftClient;

/**
 * Aperture's client-side Optifine probes (roadmap P218.1), re-pointed at
 * 1.20.4 equivalents.
 *
 * <p><b>Not the same class as {@link mchorse.mclib.utils.OptifineHelper}.</b>
 * McLib's helper (P217) answers render-pass questions — is this the shadow
 * pass, notify the per-entity shader id. Aperture's — this one — answers four
 * unrelated <i>settings/state</i> questions for the camera system. Legacy
 * shipped both, in both packages, with the same class name; the port keeps both
 * names so every call-site diffs 1:1 against
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/OptifineHelper.java}.</p>
 *
 * <h2>What each probe became</h2>
 * <table border="1">
 *   <caption>legacy probe → 1.20.4 backing</caption>
 *   <tr><th>legacy</th><th>1.20.4</th></tr>
 *   <tr><td>{@code Shaders.shaderPackLoaded}</td>
 *       <td>{@link IrisCompat#isShaderPackInUse()} (Iris' v0 API); {@code false}
 *           with no Iris</td></tr>
 *   <tr><td>{@code ofCameraZoom} key held</td>
 *       <td>nothing — see {@link #isZooming()}</td></tr>
 *   <tr><td>{@code GameSettings.ofDynamicFov}</td>
 *       <td>vanilla's "FOV Effects" accessibility slider,
 *           {@code GameOptions.getFovEffectScale()} ({@code SimpleOption<Double>},
 *           javap-verified against the yarn 1.20.4 named jar)</td></tr>
 *   <tr><td>{@code Config.isFogFast/isFogFancy}</td>
 *       <td>constants {@code false}/{@code true} — 1.20.4 vanilla fog is the
 *           "fancy" one and the fast/fancy toggle is gone</td></tr>
 * </table>
 *
 * <h2>Why the legacy static block is not ported</h2>
 *
 * <p>Legacy scanned {@code GameSettings.class.getDeclaredFields()} for
 * {@code of}-prefixed fields, taking the <i>first</i> whose type name contains
 * {@code KeyBinding} as the zoom key and the one literally named
 * {@code ofDynamicFov} as the dynamic-FOV setting — Optifine injects both into
 * the vanilla class, so they only exist on an Optifine install. There is no
 * such injection on Fabric: {@code GameOptions} has the FOV-effects option as a
 * real, public, named method, and no zoom key at all. The scan therefore has
 * nothing to scan for and the whole block is dropped; the four methods it fed
 * are what call-sites actually use, and they all still exist here.</p>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/OptifineHelper.java
 */
public class OptifineHelper
{
    /**
     * Legacy: {@code Class.forName("Config")} + {@code "net.optifine.shaders.Shaders"}
     * both resolved, i.e. "this install can have shader packs at all". The
     * modern equivalent is "Iris is installed"; like legacy's, it is a one-shot
     * constant because a mod cannot appear mid-run.
     *
     * <p>Its only legacy consumer is Aperture's config registration, which
     * hides the whole {@code optifine} category when no shader stack exists
     * (legacy {@code ClientProxy} line 292) — that lands with P218.</p>
     */
    public static final boolean shaderpackSupported = IrisCompat.isLoaded();

    /**
     * Test seam for {@link #dynamicFov()} — the FOV-effects scale, live-read
     * from {@code GameOptions} when unset. A supplier that throws exercises the
     * load-bearing {@code true} default.
     */
    public static DoubleSupplier fovEffectScale;

    /**
     * The one place a zoom bridge would ever attach (see {@link #isZooming()}).
     * Production installs nothing; it exists so the FOV-capture path stays
     * exercisable.
     */
    public static BooleanSupplier zoomProvider;

    private OptifineHelper()
    {}

    /**
     * Whether a shader pack is currently active. Legacy read
     * {@code Shaders.shaderPackLoaded} behind its {@code shaderpackSupported}
     * latch; this reads Iris' {@code isShaderPackInUse()} behind the same
     * "is the shader mod even here" gate inside {@link IrisCompat}.
     *
     * <p>Gates the shader-curve half of {@code CurveManager.refreshCurves()}
     * (P218), which is why it must answer {@code false} — not throw — on every
     * vanilla install.</p>
     */
    public static boolean isShaderLoaded()
    {
        return IrisCompat.isShaderPackInUse();
    }

    /**
     * Whether the user is holding Optifine's zoom key.
     *
     * <p><b>Always {@code false} on 1.20.4</b> — S21 open question 4, closed in
     * favour of strict parity. The reasoning: the port's behavior bar is
     * 1.12.2 <i>Blockbuster</i>, and baseline 1.12.2 had no Optifine either, so
     * "no zoom key is ever down" <i>is</i> the baseline answer. The consumer is
     * {@code RenderFrame.fromPlayer}, which multiplies the captured FOV by
     * {@code 0.25} while zooming; wiring this to some modern zoom mod
     * (Zoomify, Ok Zoomer, …) would silently change recorded manual-fixture FOV
     * relative to the baseline, and wiring it to <i>our own</i> camera-editor
     * zoom would do the same for a completely unrelated gesture. Neither is a
     * bug fix, so neither happens by default.</p>
     *
     * <p>The multiplier path itself is kept live (via
     * {@code RenderFrame.zooming}) rather than deleted, so that a future
     * opt-in bridge is a one-line supplier install and not a re-port. Set
     * {@link #zoomProvider} to drive it.</p>
     */
    public static boolean isZooming()
    {
        BooleanSupplier provider = zoomProvider;

        return provider != null && provider.getAsBoolean();
    }

    /**
     * Whether FOV effects apply — legacy read Optifine's {@code ofDynamicFov}
     * boolean, 1.20.4's equivalent is the "FOV Effects" accessibility slider
     * ({@code 0} = no FOV effects, up to {@code 1} = full).
     *
     * <p><b>The failure default is {@code true} and it is load-bearing</b>
     * (legacy comment: "default minecraft value is true"): recording FOV math
     * assumed effects-on when it could not tell, so every failure path — no
     * {@code MinecraftClient} (headless), no options, a throwing probe — must
     * return {@code true}, never {@code false}. Legacy swallowed the exception
     * exactly here, and so does this.</p>
     */
    public static boolean dynamicFov()
    {
        try
        {
            DoubleSupplier seam = fovEffectScale;

            if (seam != null)
            {
                return seam.getAsDouble() > 0;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            return mc.options.getFovEffectScale().getValue() > 0;
        }
        catch (Throwable t)
        {}

        return true; //default minecraft value is true
    }

    /**
     * Legacy {@code Config.isFogFast()} — Optifine's "Fog: Fast" render
     * setting. 1.20.4 has no fast/fancy fog split, so this is a constant
     * {@code false}; {@code ShaderCenterDepthCurve} (P218) then never takes its
     * {@code far *= 0.83F} branch, matching an Optifine install left on its
     * default.
     */
    public static boolean isFogFast()
    {
        return false;
    }

    /**
     * Legacy {@code Config.isFogFancy()} — constant {@code true} on 1.20.4:
     * vanilla fog is the fancy one, so {@code ShaderCenterDepthCurve} (P218)
     * always applies {@code far *= 0.95F}.
     *
     * <p>Note the asymmetry with legacy, which defaulted <i>both</i> to
     * {@code false} when {@code Config} was missing. That default described "no
     * Optifine, so no fog mode to report"; here the question is answerable —
     * 1.20.4 <i>has</i> a fog mode and it is fancy — so answering {@code true}
     * is the faithful port of what Optifine reported, not of what its absence
     * reported.</p>
     */
    public static boolean isFogFancy()
    {
        return true;
    }

    /** Test-only: drop both seams. */
    public static void reset()
    {
        fovEffectScale = null;
        zoomProvider = null;
    }
}
