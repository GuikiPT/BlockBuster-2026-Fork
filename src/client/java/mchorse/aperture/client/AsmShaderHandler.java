package mchorse.aperture.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * The shader-curve value sink — legacy {@code AsmShaderHandler}, reduced to its
 * public state (roadmap P218, part 1).
 *
 * <p><b>The name is an ASM artefact and it is kept on purpose.</b> In 1.12.2
 * this class was Aperture's coremod hook target: every static here was written
 * by a keyframe curve and read back by ASM-injected calls inside Optifine's
 * {@code net.optifine.shaders.Shaders} ({@code setProgramUniform1i/1f},
 * {@code updateOptionUniforms}, {@code afterInit}, {@code loadShaderPack}) plus
 * the GLSL source rewriter. Fabric has no coremod, so the hook bodies move to
 * an Iris platform layer (P218 part 2,
 * {@code mchorse.blockbuster.client.compat.iris}) — but the plan explicitly
 * keeps <i>this facade</i> so the ported curve classes in
 * {@code mchorse.aperture.camera.curves} still diff 1:1 against
 * {@code .tools/legacy-src/aperture/.../camera/curves/*.java}.</p>
 *
 * <h2>What is here and what is not</h2>
 *
 * <p>Here: the three public statics the curve classes touch
 * ({@link #uniform1i}, {@link #uniform1f}, {@link #sunPathRotation}) and
 * {@link #uniformPrefix}. Not here: the regexes
 * ({@code PATTERN_DEFINE}, {@code PATTERN_IF}, {@code PATTERN_CONST},
 * {@code PATTERN_CASE}, {@code PATTERN_ARRAY}), the source caches
 * ({@code cachedShaders}, {@code cachedIncludes}), the {@code CodelineParser}
 * instances, the option classification classes ({@code ShaderUniformOption},
 * {@code ShaderUniformConstOption}) and every hook body — those are the GLSL
 * rewriter's, and the rewriter is Iris-shaped now.</p>
 *
 * <p><b>Also not here any more: {@code option1i}/{@code option1f} and
 * {@code loadShaderPack()} (S22 P269, batch W-D).</b> Legacy's two discovered
 * -option registries were {@code Map<String, ShaderUniform1i/1f>} and had three
 * consumers — {@code CurveManager.refreshCurves()}, {@code updateOptionUniforms()}
 * and {@code afterInit()}. All three <i>are</i> ported, and none of them reads
 * this class: the registries live in
 * {@link mchorse.blockbuster.client.compat.iris.ShaderCurveBridge#option1i}/
 * {@code option1f} typed {@code Map<String, OptionUniform>}, the curve list comes
 * off {@link mchorse.aperture.camera.CurveManager.ShaderOptionSource}, the
 * per-frame push is an Iris custom uniform, and the pack-load clear is
 * {@code ShaderCurveBridge.onPackLoadStart()}. The facade's copies were declared
 * {@code Object}-valued in P218 part 1 and part 2 then declared its own typed
 * pair, so the facade's had <b>zero writers in any source set</b> — a
 * permanently empty shadow of a live registry under the same name, which is the
 * exact shape that made the {@code uniform1i} duplication a real defect until the
 * batch R merge aliased the two. {@code loadShaderPack()} cleared only those two
 * maps, so it was a no-op by construction; it went with them. The curve classes
 * never referenced either, so facade diff-ability against
 * {@code .tools/legacy-src} is unaffected where it is load-bearing.</p>
 *
 * <h2>Contract for the platform layer</h2>
 *
 * <ul>
 *   <li>{@link #uniform1f} / {@link #uniform1i} are the <b>override</b> maps.
 *       A present key means "a keyframe channel is driving this uniform right
 *       now, use my value instead of yours"; an absent key means pass-through.
 *       Legacy consulted them inside {@code setProgramUniform1f/1i}; the Iris
 *       port consults them from its custom-uniform suppliers. Keys are raw
 *       uniform names ({@code centerDepthSmooth}, {@code rainStrength},
 *       {@code _uniform_SHADOW_QUALITY}, …), never curve ids.</li>
 *   <li>{@link #sunPathRotation} is the pack's <b>captured default</b>, written
 *       once per pack load (legacy {@code afterInit()}); {@link #sunPathRotationSink}
 *       is the write side that replaced legacy's direct
 *       {@code Shaders.sunPathRotation = value} assignment and, on Iris,
 *       <b>correctly stays a no-op</b> — see its javadoc.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/AsmShaderHandler.java
 */
public class AsmShaderHandler
{
    /**
     * Legacy {@code uniformPrefix}. An option named {@code SHADOW_QUALITY}
     * becomes the GLSL uniform {@code _uniform_SHADOW_QUALITY} while its curve
     * id stays {@code shader_SHADOW_QUALITY} — the prefix must never leak into
     * a profile's {@code curves} key or 1.12.2 profiles stop matching.
     */
    public static final String uniformPrefix = "_uniform_";

    /**
     * Curve-driven integer uniform overrides, keyed by uniform name. Written by
     * {@link mchorse.aperture.camera.curves.ShaderUniform1iCurve} and cleared
     * by its {@code reset()}.
     */
    public static final Map<String, Integer> uniform1i = new HashMap<String, Integer>();

    /**
     * Curve-driven float uniform overrides, keyed by uniform name. Written by
     * {@link mchorse.aperture.camera.curves.ShaderUniform1fCurve} and cleared
     * by its {@code reset()}.
     */
    public static final Map<String, Float> uniform1f = new HashMap<String, Float>();

    /**
     * The shader pack's own {@code sunPathRotation}, captured on pack load
     * (legacy {@code afterInit()}: {@code sunPathRotation = Shaders.sunPathRotation},
     * plus the lazy capture inside {@code setProgramUniform1f}).
     * {@link mchorse.aperture.camera.curves.ShaderSunPathRotationCurve#reset()}
     * restores it, which is why it is a plain field and not derived — legacy
     * relied on "whatever the pack last told us" surviving the curve.
     */
    public static float sunPathRotation;

    /**
     * The write side of {@code sunPathRotation}, replacing legacy's direct
     * {@code Shaders.sunPathRotation = (float) value} assignment.
     *
     * <p>Unlike every other shader curve, sun-path rotation was not routed
     * through the uniform-override map alone: Optifine recomputed the celestial
     * matrix from the <i>live</i> static {@code Shaders.sunPathRotation}, so
     * legacy wrote both. Note those were two <b>distinct</b> fields —
     * {@code AsmShaderHandler.sunPathRotation} (this class) held the pack's
     * captured default and was the restore target for
     * {@code ShaderSunPathRotationCurve.reset()}, while
     * {@code Shaders.sunPathRotation} held the live per-frame value.</p>
     *
     * <p><b>S22/P242 — this stays a no-op on Iris, on purpose.</b> Iris' live
     * read is {@code PackDirectives.getSunPathRotation()}, and
     * {@code PackDirectivesMixin} already overrides it from the very same
     * uniform-override map the curve writes (which is also what makes the
     * celestial bodies and the shadow projection move together, as they did on
     * Optifine). There is therefore no second destination left for a field
     * write: the only field in reach is {@link #sunPathRotation} — the
     * <i>captured default</i> — and pointing the sink at it would overwrite the
     * restore target with the curve's own keyframe, so {@code reset()} would
     * restore the last played value instead of the pack's. See
     * {@code plan/inbox/batchR-A.md} and
     * {@code ShaderCurveClientWiring}'s "Why sunPathRotationSink stays a no-op".
     * The seam is kept (rather than deleted) because it is the platform hook a
     * future non-Iris backend with a live static would need.</p>
     *
     * <p>Values arrive already narrowed through {@code (float)} by the curve,
     * matching legacy's cast.</p>
     */
    public static DoubleConsumer sunPathRotationSink = (value) -> {};

    protected AsmShaderHandler()
    {}

    /**
     * Drop every curve-driven override. Called when playback stops (through
     * {@code CurveManager.resetAll()} the curves clear their own keys, so this
     * is the belt-and-braces variant used by tests and by pack reloads).
     */
    public static void reset()
    {
        uniform1i.clear();
        uniform1f.clear();
    }
}
