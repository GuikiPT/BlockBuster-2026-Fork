package mchorse.aperture.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mchorse.aperture.Aperture;
import mchorse.aperture.camera.curves.AbstractCurve;
import mchorse.aperture.camera.curves.BrightnessCurve;
import mchorse.aperture.camera.curves.ShaderCenterDepthCurve;
import mchorse.aperture.camera.curves.ShaderFloatOptionCurve;
import mchorse.aperture.camera.curves.ShaderIntegerOptionCurve;
import mchorse.aperture.camera.curves.ShaderSunPathRotationCurve;
import mchorse.aperture.camera.curves.ShaderUniform1fCurve;
import mchorse.aperture.camera.curves.ShaderUniform1iCurve;
import mchorse.aperture.camera.curves.ShaderWorldTimeCurve;
import mchorse.aperture.camera.curves.VanillaAsmCurve;
import mchorse.aperture.camera.values.ValueCurves;
import mchorse.aperture.client.AsmRenderingHandler.Curve;
import mchorse.aperture.utils.OptifineHelper;
import mchorse.mclib.utils.keyframes.KeyframeChannel;

/**
 * Per-profile keyframe-curve driver (P180 vanilla subset + P218 shader family).
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/camera/CurveManager.java}.</p>
 *
 * <h2>Registration order is UI order — and disk order</h2>
 *
 * <p>{@link #curves} is a {@link LinkedHashMap} and {@code GuiCurves} lists it
 * in iteration order, so {@link #refreshCurves()}'s statement order <i>is</i> the
 * editor's curve list. The ids are also the JSON keys inside a profile's
 * {@code "curves"} object, so they must match 1.12.2 byte-for-byte. The full
 * legacy order:</p>
 *
 * <ol>
 *   <li>{@code brightness}</li>
 *   <li>one {@link VanillaAsmCurve} per {@link Curve} constant, keyed
 *       {@code curve.name().toLowerCase()} — {@code skyr, skyg, skyb, cloudr,
 *       cloudg, cloudb, fogr, fogg, fogb, fogstart, fogend, fogdensity,
 *       celestialangle}</li>
 *   <li><b>only when a shader pack is loaded</b>
 *       ({@link OptifineHelper#isShaderLoaded()}, backed by Iris'
 *       {@code isShaderPackInUse()} through {@code IrisCompat}):
 *       <ul>
 *         <li>{@code shader_sun_path_rotation} — <b>double-gated</b>, also needs
 *             {@code Aperture.optifineShaderOptionCurve}</li>
 *         <li>{@code shader_center_depth}, {@code shader_rain_strength},
 *             {@code shader_wetness}, {@code shader_frame_time},
 *             {@code shader_world_time}, {@code shader_in_water}</li>
 *         <li>{@code shader_<OPTION>} per discovered option uniform, in four
 *             groups: visible floats, visible ints, hidden floats, hidden ints —
 *             each group sorted by display name (see
 *             {@link #getSortedOptions(boolean, boolean)})</li>
 *       </ul></li>
 * </ol>
 *
 * <p>Two deliberate omissions from the legacy body: the bare
 * {@code Shaders.getShaderOption(id);} statement inside each option loop (its
 * result was discarded — dead code), and the {@code option1f}/{@code option1i}
 * map reads, which the {@link ShaderOptionSource} seam replaces (registration
 * needs display names and visibility, which those maps did not carry).</p>
 *
 * <h2>Reset every frame</h2>
 *
 * <p>{@link #applyCurves} calls {@code reset()} on every curve whose channel is
 * missing <i>or empty</i>, on every single frame. That is how a uniform
 * un-sticks when the artist clears a channel mid-edit; skipping the reset when
 * nothing changed would leave the last applied value latched.</p>
 */
public class CurveManager
{
    /**
     * The installed shader-option source. Never null — {@link ShaderOptionSource#EMPTY}
     * is the shipped state until the Iris platform layer installs a real one.
     */
    private static ShaderOptionSource optionSource = ShaderOptionSource.EMPTY;

    public Map<String, AbstractCurve> curves = new LinkedHashMap<String, AbstractCurve>();

    /* --------------------------------------------------------------------- */
    /* The P218 part-2 seam                                                   */
    /* --------------------------------------------------------------------- */

    /**
     * One discovered shader-pack option, normalised — the port's stand-in for
     * Optifine's {@code net.optifine.shaders.config.ShaderOption} (and, on the
     * modern side, for Iris' {@code OptionMenuElement}/{@code ShaderPackOptions}
     * types). Nothing outside the platform layer may name those.
     *
     * @param id          the pack's option identifier, e.g. {@code SHADOW_QUALITY}
     *                    — legacy {@code ShaderOption.getName()}. This is what the
     *                    curve id ({@code "shader_" + id}) and the uniform name
     *                    ({@code "_uniform_" + id}) are built from, so it must be
     *                    the <b>macro name as it appears in the GLSL source</b>,
     *                    unprefixed and untranslated. Never null or empty.
     * @param displayName the pack's human label — legacy
     *                    {@code ShaderOption.getNameText()}, i.e. the shader lang
     *                    file's translation, falling back to {@code id} when the
     *                    pack ships no label. It is the <b>sort key</b> for the
     *                    curve list, and {@code ShaderFloatOptionCurve
     *                    .getTranslatedName()} shows {@code "<displayName>/<id>"}
     *                    when it differs from {@code id}. Never null; pass
     *                    {@code id} rather than null when there is no label.
     * @param integer     {@code true} → the option is registered as a
     *                    {@link ShaderIntegerOptionCurve} and pushed through
     *                    {@code uniform1i}; {@code false} → float. Legacy derived
     *                    this from {@code ShaderUniformOption.uniformType}: every
     *                    allowed value of the option parses as an {@code int} ⇒
     *                    INTEGER, otherwise FLOAT. Options that classified as
     *                    {@code NOT_SUPPORT} (non-numeric values, {@code __} or
     *                    {@code gl_} names, or demoted by {@code #if}/{@code case}/
     *                    array usage) must <b>not</b> appear in
     *                    {@link ShaderOptionSource#getOptions()} at all.
     * @param visible     legacy {@code ShaderOption.isVisible()} — whether the
     *                    option is exposed in the pack's option screens. Visible
     *                    options are registered before hidden ones; that is the
     *                    only thing this flag does here.
     */
    public record ShaderOption(String id, String displayName, boolean integer, boolean visible)
    {}

    /**
     * The pluggable supplier of discovered shader-pack option uniforms — the
     * single seam between this (pure, headless, Iris-free) curve layer and the
     * Iris platform layer of P218 part 2.
     *
     * <h2>Contract</h2>
     *
     * <ul>
     *   <li>{@link #getOptions()} returns <b>every option that became a
     *       keyframeable uniform</b> — i.e. every option the GLSL rewriter
     *       actually rewrote into {@code #define OPT _uniform_OPT} plus a
     *       {@code uniform int|float _uniform_OPT;} declaration. An option that
     *       was discovered but rejected (unsupported value set, reserved name,
     *       demoted by macro/case/array usage) must be absent, not present with
     *       some "unsupported" marker.</li>
     *   <li>The returned list must <b>exclude {@code "sunPathRotation"}</b>. It
     *       is an option on the pack side but the port registers it as the
     *       dedicated, separately-gated {@code shader_sun_path_rotation} curve;
     *       legacy filtered it inside {@code getSortedOptions} and this seam
     *       moves the filter to the source. {@link #getSortedOptions} filters it
     *       again defensively, so a source that forgets is merely redundant, not
     *       broken.</li>
     *   <li>Order should be <b>discovery order</b> (legacy's
     *       {@code LinkedHashMap} insertion order over {@code option1f} then
     *       {@code option1i}). {@link #getSortedOptions} re-sorts by display
     *       name with a <i>stable</i> sort, so this order is what breaks ties
     *       between options sharing a label. Any order is accepted; only ties
     *       are affected.</li>
     *   <li>Ids must be unique across the whole list, including across the
     *       int/float split. Two entries with the same id would register the
     *       same curve id twice and the later one would win.</li>
     *   <li>It is called from {@link #refreshCurves()}, i.e. on camera-editor
     *       open and on shader-pack (re)load — <b>not</b> per frame. It may do
     *       real work, but it must be cheap enough to run on the render thread
     *       and must never throw: an exception here aborts curve registration
     *       and leaves the editor with no curves at all.</li>
     *   <li>It must answer sensibly when no pack is loaded (return
     *       {@link Collections#emptyList()}); {@link #refreshCurves()} does not
     *       call it in that state today, but {@link #findOption} may.</li>
     * </ul>
     *
     * <p>Install with {@link CurveManager#setOptionSource(ShaderOptionSource)}.
     * Passing {@code null} restores {@link #EMPTY}. Installing does <b>not</b> refresh
     * the curve table — call {@code ClientProxy.curveManager.refreshCurves()}
     * afterwards (the pack-load callback does both).</p>
     */
    public interface ShaderOptionSource
    {
        /** The shipped state: no Iris, no pack, no option curves. */
        ShaderOptionSource EMPTY = Collections::emptyList;

        /**
         * Every keyframeable shader-pack option, in discovery order. Must never
         * return null and never throw; return an empty list when there is
         * nothing to report.
         */
        List<ShaderOption> getOptions();
    }

    /**
     * Install (or replace) the discovered-option source. {@code null} restores
     * {@link ShaderOptionSource#EMPTY}. Static because legacy's equivalent
     * ({@code Shaders.getShaderOption}) was static and the option curves reach
     * it from their {@code getTranslatedName()} without holding a manager.
     */
    public static void setOptionSource(ShaderOptionSource source)
    {
        optionSource = source == null ? ShaderOptionSource.EMPTY : source;
    }

    /** Never null. */
    public static ShaderOptionSource getOptionSource()
    {
        return optionSource;
    }

    /**
     * Legacy {@code Shaders.getShaderOption(id)} — the display-name lookup the
     * option curves do. Linear scan, called only when the GUI renders a label;
     * {@code null} when the id is unknown (a profile referencing an option the
     * current pack does not have), which the curves handle by falling back to
     * the bare id.
     */
    public static ShaderOption findOption(String id)
    {
        for (ShaderOption option : optionSource.getOptions())
        {
            if (option.id().equals(id))
            {
                return option;
            }
        }

        return null;
    }

    /* --------------------------------------------------------------------- */
    /* Registration                                                           */
    /* --------------------------------------------------------------------- */

    public void refreshCurves()
    {
        this.curves.clear();

        this.curves.put("brightness", new BrightnessCurve());

        for (Curve curve : Curve.values())
        {
            this.curves.put(curve.name().toLowerCase(), new VanillaAsmCurve(curve));
        }

        if (OptifineHelper.isShaderLoaded())
        {
            if (Aperture.optifineShaderOptionCurve.get())
            {
                this.curves.put("shader_sun_path_rotation", new ShaderSunPathRotationCurve());
            }

            this.curves.put("shader_center_depth", new ShaderCenterDepthCurve());
            this.curves.put("shader_rain_strength", new ShaderUniform1fCurve("rainStrength"));
            this.curves.put("shader_wetness", new ShaderUniform1fCurve("wetness"));
            this.curves.put("shader_frame_time", new ShaderUniform1fCurve("frameTimeCounter"));
            this.curves.put("shader_world_time", new ShaderWorldTimeCurve());
            this.curves.put("shader_in_water", new ShaderUniform1iCurve("isEyeInWater"));

            for (String id : this.getSortedOptions(true, false))
            {
                this.curves.put("shader_" + id, new ShaderFloatOptionCurve(id));
            }

            for (String id : this.getSortedOptions(true, true))
            {
                this.curves.put("shader_" + id, new ShaderIntegerOptionCurve(id));
            }

            for (String id : this.getSortedOptions(false, false))
            {
                this.curves.put("shader_" + id, new ShaderFloatOptionCurve(id));
            }

            for (String id : this.getSortedOptions(false, true))
            {
                this.curves.put("shader_" + id, new ShaderIntegerOptionCurve(id));
            }
        }
    }

    public void applyCurves(ValueCurves value, long progress, float partialTick)
    {
        float tick = progress + partialTick;

        for (String id : this.curves.keySet())
        {
            AbstractCurve curve = this.curves.get(id);
            KeyframeChannel channel = value.get(id);

            if (channel != null && !channel.isEmpty())
            {
                curve.apply(channel.interpolate(tick));
            }
            else
            {
                curve.reset();
            }
        }
    }

    public void resetAll()
    {
        for (AbstractCurve curve : this.curves.values())
        {
            curve.reset();
        }
    }

    /**
     * The ids of every discovered option matching {@code (visible, integer)},
     * sorted by display name.
     *
     * <p>Legacy's signature was
     * {@code getSortedOptions(Collection<String> keys, boolean visible)} — it
     * took the key set of {@code AsmShaderHandler.option1f} or
     * {@code option1i} (which is where the int/float split came from) and
     * filtered by {@code ShaderOption.isVisible()}. The port folds the map
     * choice into the {@code integer} parameter because the split now lives on
     * {@link ShaderOption#integer()}.</p>
     *
     * <p>{@code "sunPathRotation"} is excluded here exactly as legacy excluded
     * it, and {@link List#sort} is stable, so options sharing a display name
     * keep the source's discovery order.</p>
     */
    public List<String> getSortedOptions(boolean visible, boolean integer)
    {
        List<ShaderOption> list = new ArrayList<ShaderOption>();

        for (ShaderOption option : optionSource.getOptions())
        {
            if (option != null
                && !"sunPathRotation".equals(option.id())
                && option.visible() == visible
                && option.integer() == integer)
            {
                list.add(option);
            }
        }

        list.sort((a, b) -> a.displayName().compareTo(b.displayName()));

        List<String> ids = new ArrayList<String>();

        for (ShaderOption option : list)
        {
            ids.add(option.id());
        }

        return ids;
    }
}
