package mchorse.aperture.utils.mclib;

import java.util.function.BooleanSupplier;

import mchorse.mclib.config.values.ValueGUI;

/**
 * The {@code optifine.option} config row (roadmap P218) — a {@link ValueGUI}
 * that persists nothing and exists only to put a toggle for
 * {@code Aperture.optifineShaderOptionCurve} into the config panel, with a
 * shader-pack reload wired to its callback.
 *
 * <p>Legacy source:
 * {@code .tools/legacy-src/aperture/src/main/java/mchorse/aperture/utils/mclib/ValueShaderOption.java},
 * registered by {@code ClientProxy.registerClientConfig} lines 287–289 as
 * {@code builder.register(new ValueShaderOption("option").clientSide())}
 * <i>after</i> the real boolean, which is itself marked {@code .invisible()}.
 * Two rows, one setting: the boolean carries the value and the file key, this
 * carries the widget. The port keeps both, and keeps the legacy category name
 * {@code "optifine"} and key {@code "shader_option_curve"}, because
 * {@code config/aperture.json} parity is the bar — an Iris-era rename would
 * orphan every existing config file.</p>
 *
 * <h2>Port split</h2>
 *
 * <p>Legacy's {@code getFields(Minecraft, GuiConfigPanel)} body lived on this
 * class. In the port, value classes are main-source and GUI bodies are
 * client-source, so the widget is contributed through the client
 * {@code ConfigGuiProviders} factory registry keyed on this class — the same
 * split {@code ValueMainButtons}/{@code BlockbusterConfigButtons} use (P208/P210).
 * This class is left holding only the <b>reload behaviour</b>, which is what
 * makes the toggle more than a plain boolean.</p>
 *
 * <h2>The reload, and the part that is not here</h2>
 *
 * <p>Legacy's callback was:</p>
 *
 * <pre>
 * if (Shaders.shaderPackLoaded)
 * {
 *     Shaders.uninit();
 *     Shaders.loadShaderPack();
 * }
 * </pre>
 *
 * <p>The reload is mandatory, not cosmetic: turning the setting on or off
 * changes whether the GLSL source rewriter injects {@code _uniform_} uniforms,
 * and that only takes effect when the pack is recompiled.</p>
 *
 * <p>Both halves are seams because the Iris types they need land with P218
 * part 2:</p>
 *
 * <ul>
 *   <li>{@link #shaderLoaded} — the {@code Shaders.shaderPackLoaded} guard. The
 *       client entrypoint installs {@code OptifineHelper::isShaderLoaded}
 *       (Iris' {@code isShaderPackInUse()} behind {@code IrisCompat}). Default
 *       {@code false}, so with nothing installed the reload never fires.</li>
 *   <li>{@link #reloadShaders} — the {@code uninit() + loadShaderPack()} pair.
 *       <b>Not implemented in part 1</b>: the Iris equivalent
 *       ({@code IrisApi.getInstance().<reload>} / {@code Iris.reload()}) is
 *       part 2's to bind, and this source set may not name an Iris type.
 *       Default is a no-op, so today the toggle changes the config value and
 *       nothing else — the new state takes effect on the next pack load.</li>
 * </ul>
 */
public class ValueShaderOption extends ValueGUI
{
    /**
     * Legacy {@code Shaders.shaderPackLoaded}. Installed by the client
     * entrypoint to {@code mchorse.aperture.utils.OptifineHelper::isShaderLoaded};
     * {@code false} until then (and forever, on an install without Iris).
     */
    public static BooleanSupplier shaderLoaded = () -> false;

    /**
     * Legacy {@code Shaders.uninit(); Shaders.loadShaderPack();}. Installed by
     * the Iris platform layer (P218 part 2); a no-op until then.
     *
     * <p>Only ever invoked through {@link #reload()}, i.e. already guarded by
     * {@link #shaderLoaded} — an implementation may assume a pack is loaded.</p>
     */
    public static Runnable reloadShaders = () -> {};

    public ValueShaderOption(String id)
    {
        super(id);
    }

    /**
     * The legacy toggle callback, verbatim in structure: reload the shader pack
     * iff one is loaded. Called by the client-side config row.
     */
    public static void reload()
    {
        if (shaderLoaded.getAsBoolean())
        {
            reloadShaders.run();
        }
    }

    /** Test-only: drop both seams back to the shipped defaults. */
    public static void resetSeams()
    {
        shaderLoaded = () -> false;
        reloadShaders = () -> {};
    }
}
