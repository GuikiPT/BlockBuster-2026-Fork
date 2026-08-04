package mchorse.aperture.camera.curves;

import java.util.function.IntSupplier;

/**
 * The "focus point" curve (roadmap P218): drives the {@code centerDepthSmooth}
 * uniform, which shader packs use as the depth-of-field focus distance.
 *
 * <p>Verbatim port of
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/curves/ShaderCenterDepthCurve.java}
 * — the keyframe value is a distance <b>in blocks</b> and the curve converts it
 * into the non-linear depth-buffer value the pack expects, using the same
 * near/far planes the game's projection matrix uses.</p>
 *
 * <h2>The formula, and the two branches that are gone</h2>
 *
 * <pre>
 * near = 0.05
 * far  = renderDistanceChunks * 32
 * if (isFogFancy()) far *= 0.95F;   // 1.12.2 Optifine
 * if (isFogFast())  far *= 0.83F;   // 1.12.2 Optifine
 * if (far &lt; 173F)   far = 173F;
 * depth = ((far + near) * value - 2 * near * far) / value / (far - near) * 0.5 + 0.5
 * </pre>
 *
 * <p>1.20.4 has no fast/fancy fog split, so P218.1 pinned
 * {@code OptifineHelper.isFogFancy()} to a constant {@code true} and
 * {@code isFogFast()} to a constant {@code false}. The fancy branch is therefore
 * <b>hardcoded</b> here ({@code far *= 0.95F}, always) and the fast branch is
 * simply absent (it was dead code on a default Optifine install too). Everything
 * else — the {@code 173F} floor, the {@code 0.05} near plane, the operator order
 * and the {@code float} literals that make {@code 0.95F} widen to
 * {@code 0.949999988079071} — is bit-identical to legacy.
 * {@code ShaderCurvesTest#fogConstantsStillJustifyTheHardcodedBranch} fails if
 * those two constants ever change, so the hardcoding cannot silently rot.</p>
 *
 * <h2>Two load-bearing quirks — do not "fix" either</h2>
 *
 * <ul>
 *   <li><b>Division by {@code value}.</b> A keyframe at exactly {@code 0} makes
 *       the expression divide by zero and produce {@code ±Infinity} (or
 *       {@code NaN} for the numerator's own zero). That happened in 1.12.2 too;
 *       the parity bar is the runtime math, so the port reproduces it. Guarding
 *       it would change what an existing profile renders.</li>
 *   <li><b>The floor is a floor, not a ceiling.</b> {@code far = max(far, 173F)}
 *       means low render distances still use a 173-block far plane. At render
 *       distance 5 the raw far is {@code 5 * 32 * 0.95 = 152}, so the clamp
 *       fires; from 6 chunks up it never does.</li>
 * </ul>
 */
public class ShaderCenterDepthCurve extends ShaderUniform1fCurve
{
    /**
     * The client's render distance in chunks — legacy
     * {@code Minecraft.getMinecraft().gameSettings.renderDistanceChunks}, on
     * 1.20.4 {@code MinecraftClient.options.getViewDistance().getValue()}
     * (javap-verified: {@code SimpleOption<Integer> getViewDistance()}).
     *
     * <p>A seam for the same reason {@code BrightnessCurve.gammaGetter} is one:
     * it keeps the curve — and its golden table — pure JUnit. The client
     * entrypoint ({@code ApertureClient}) installs the live read; the headless
     * default is {@code 12}, vanilla's default render distance.</p>
     *
     * <p>Note it is the <i>configured</i> view distance, not
     * {@code getClampedViewDistance()}: legacy read the raw setting, and the
     * server's cap has no bearing on where the pack should focus.</p>
     */
    public static IntSupplier renderDistance = () -> 12;

    public ShaderCenterDepthCurve()
    {
        super("centerDepthSmooth");
    }

    @Override
    public void apply(double value)
    {
        double near = 0.05;
        double far = renderDistance.getAsInt() * 32;

        /* Legacy: if (OptifineHelper.isFogFancy()) far *= 0.95F;
         * — constant true on 1.20.4 (P218.1), so the branch is hardcoded. */
        far *= 0.95F;

        /* Legacy: if (OptifineHelper.isFogFast()) far *= 0.83F;
         * — constant false on 1.20.4 (P218.1), so the branch is dropped. */

        if (far < 173F)
        {
            far = 173F;
        }

        double depth = ((far + near) * value - 2 * near * far) / value / (far - near) * 0.5 + 0.5;

        super.apply(depth);
    }
}
