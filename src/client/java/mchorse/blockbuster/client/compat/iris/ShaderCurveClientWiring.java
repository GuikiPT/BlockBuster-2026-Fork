package mchorse.blockbuster.client.compat.iris;

import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.Iris;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CurveManager;
import mchorse.aperture.utils.mclib.ValueShaderOption;
import mchorse.blockbuster.Blockbuster;

/**
 * Joins the two halves of S21 P218 (batch R merge step).
 *
 * <p>P218 landed as two independent worktrees: <b>R-A</b> built the pure curve
 * layer ({@code mchorse.aperture.camera.curves.*}, {@code CurveManager}, the
 * {@code AsmShaderHandler} facade, the {@code optifine.shader_option_curve}
 * config row) with no Iris type anywhere, and <b>R-B</b> built the Iris platform
 * layer ({@link ShaderCurveBridge}, the GLSL rewriter, option discovery, the six
 * mixins). Each was written against a seam the other had not shipped yet, so
 * <b>no agent suite covered the two being wired to each other</b>.</p>
 *
 * <h2>What the merge had to reconcile</h2>
 *
 * <ul>
 *   <li><b>Two override maps.</b> Both halves declared their own
 *       {@code uniform1f}/{@code uniform1i}: the curve classes write the
 *       facade's, the Iris push side reads {@link ShaderCurveBridge}'s. They are
 *       now literally the same instance (aliased in {@code ShaderCurveBridge}),
 *       because a keyframe written into a map nothing reads is a curve that
 *       silently does nothing.</li>
 *   <li><b>Two {@code sunPathRotation} fields.</b> Same problem, but a primitive
 *       cannot be aliased, so the bridge's field became the accessor pair
 *       {@link ShaderCurveBridge#sunPathRotation()} over the facade's field —
 *       the one {@code ShaderSunPathRotationCurve.reset()} restores from.</li>
 *   <li><b>The option record lost {@code visible}.</b> R-B's
 *       {@code describeOptions()} returns options pre-sorted into legacy's four
 *       registration groups, but {@code CurveManager.getSortedOptions(visible,
 *       integer)} re-derives those groups by filtering, so the flag had to be
 *       carried rather than baked into list order.</li>
 * </ul>
 *
 * <h2>Why {@code sunPathRotationSink} stays a no-op</h2>
 *
 * <p>Legacy wrote sun-path rotation twice — into the uniform map <i>and</i> into
 * Optifine's live {@code Shaders.sunPathRotation} static, because the engine read
 * the static to place the celestial bodies. Those were two different fields:
 * {@code AsmShaderHandler.sunPathRotation} held the pack's captured default (the
 * restore target), {@code Shaders.sunPathRotation} held the live value. On Iris
 * the live read is {@code PackDirectives.getSunPathRotation()}, which
 * {@code PackDirectivesMixin} already overrides from the same uniform map, so the
 * second write has no destination — and pointing the sink at the facade field
 * would overwrite the restore target with the curve's own value, making
 * {@code reset()} restore a keyframe instead of the pack default.</p>
 *
 * <p>Everything here is inert without Iris: with no pack loaded nothing is
 * discovered, {@code describeOptions()} is empty, and {@code CurveManager}
 * registers exactly the vanilla curve set.</p>
 */
public final class ShaderCurveClientWiring
{
    private ShaderCurveClientWiring()
    {}

    /**
     * Installs every P218 cross-half seam. Called once from client init, after
     * {@code ApertureClient} has installed the non-Iris seams it owns and
     * <b>before</b> its first {@code refreshCurves()}.
     */
    public static void install()
    {
        /* Discovered option uniforms -> the curve registry. R-B returns them
         * already in legacy registration order; CurveManager re-groups them by
         * (visible, integer) anyway, which reproduces that order exactly. */
        CurveManager.setOptionSource(ShaderCurveClientWiring::describeOptions);

        /* Legacy's null-config branch was "no config object => behave as if on",
         * which is why the bridge's default is true rather than false. */
        ShaderCurveBridge.optionCurvesEnabled = () ->
            Aperture.optifineShaderOptionCurve == null || Aperture.optifineShaderOptionCurve.get();

        /* A pack (re)load discovers a different option set, so the curve table
         * has to be rebuilt or the editor keeps the previous pack's rows. */
        ShaderCurveBridge.curveRefresh = () -> ClientProxy.curveManager.refreshCurves();

        /* Legacy ValueShaderOption toggled the option-uniform machinery and then
         * forced Optifine to re-read the pack (Shaders.uninit() +
         * loadShaderPack()), because the rewrite happens at pack-parse time and
         * a toggle changes what the rewriter emits. Iris' equivalent is
         * Iris.reload(); it only makes sense with Iris present. */
        if (IrisCompat.isLoaded())
        {
            ValueShaderOption.reloadShaders = IrisShaderReload::reload;
        }
    }

    /**
     * Adapts {@link ShaderCurveBridge.ShaderOptionInfo} to
     * {@link CurveManager.ShaderOption}. Two records rather than one shared type
     * because the curve layer must not name a class in a package that compiles
     * against Iris.
     */
    static List<CurveManager.ShaderOption> describeOptions()
    {
        List<ShaderCurveBridge.ShaderOptionInfo> described = ShaderCurveBridge.describeOptions();
        List<CurveManager.ShaderOption> out = new ArrayList<CurveManager.ShaderOption>(described.size());

        for (ShaderCurveBridge.ShaderOptionInfo info : described)
        {
            out.add(new CurveManager.ShaderOption(info.id(), info.displayName(), info.integer(), info.visible()));
        }

        return out;
    }

    /**
     * The one Iris-typed call, isolated in its own class so
     * {@link ShaderCurveClientWiring} loads with no Iris on the classpath.
     */
    private static final class IrisShaderReload
    {
        private static void reload()
        {
            try
            {
                Iris.reload();
            }
            catch (Exception | LinkageError e)
            {
                /* Legacy precedent: a shader reload that fails logs and is
                 * dropped — it never takes the config screen down with it. */
                Blockbuster.LOGGER.error("Failed to reload Iris shaders after toggling the option-curve config", e);
            }
        }
    }
}
