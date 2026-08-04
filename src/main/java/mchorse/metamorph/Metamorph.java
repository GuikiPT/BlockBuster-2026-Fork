package mchorse.metamorph;

import java.util.function.BooleanSupplier;
import mchorse.mclib.config.ConfigBuilder;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.config.values.ValueInt;
import mchorse.mclib.events.RegisterConfigEvent;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundled Metamorph static holder (roadmap S4).
 *
 * <p>Legacy {@code mchorse.metamorph.Metamorph} was the Forge {@code @Mod}
 * class whose config {@code Value*} statics were populated inside
 * {@code onConfigRegister(RegisterConfigEvent)}. The S4 morph-data core
 * (P47–P51) compiles against a handful of these statics ({@code disablePov}
 * in {@code AbstractMorph.updateSizeDefault}/{@code getEyeHeight},
 * {@code renderBodyPartAxis} in the S6 body-part renderer,
 * {@code morphInTightSpaces}/{@code disableHealth} in P48/P52), so this file
 * declares the full legacy config set now, initialized inline with the legacy
 * defaults so pre-registration reads are safe (legacy left them null until
 * config registration ran at startup).</p>
 *
 * <p>Ids, defaults, categories and {@code clientSide()}/{@code syncable()}
 * markings are legacy-exact (see {@code onConfigRegister} in
 * .tools/legacy-src/metamorph/.../Metamorph.java) — the config-file format and
 * OP-sync behavior are user-facing 1.12.2 parity surface. The FML
 * lifecycle (@Mod, proxies, channel, commands) is intentionally omitted; it
 * lands with its owning phases (networking P55, commands P60).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/Metamorph.java
 */
public class Metamorph
{
    /* Metadata fields */
    public static final String MOD_ID = "metamorph";
    public static final String MODNAME = "Metamorph";
    public static final String VERSION = "1.4";

    public static Logger LOGGER = LoggerFactory.getLogger("Metamorph");

    /* Configuration (legacy defaults; see class javadoc) */

    /* OP-access category "metamorph" */
    public static ValueBoolean opEntitySelector = new ValueBoolean("entity_selectors", true);

    /* Category "acquiring" */
    public static ValueBoolean preventGhosts = new ValueBoolean("prevent_ghosts", true);
    public static ValueBoolean preventKillAcquire = new ValueBoolean("prevent_kill_acquire", false);
    public static ValueBoolean acquireImmediately = new ValueBoolean("acquire_immediately", false);

    /* Category "morphs" */
    public static ValueBoolean keepMorphs = new ValueBoolean("keep_morphs", true);
    public static ValueBoolean disablePov = new ValueBoolean("disable_pov", false);
    public static ValueBoolean disableHealth = new ValueBoolean("disable_health", false);
    public static ValueBoolean disableMorphAnimation = new ValueBoolean("disable_morph_animation", false);
    public static ValueBoolean disableMorphDisguise = new ValueBoolean("disable_morph_disguise", false);
    public static ValueBoolean disableFirstPersonHand = new ValueBoolean("disable_first_person_hand", false);
    public static ValueBoolean morphInTightSpaces = new ValueBoolean("morph_in_tight_spaces", false);
    public static ValueBoolean showMorphIdleSounds = new ValueBoolean("show_morph_idle_sounds", true);
    public static ValueBoolean pauseGUIInSP = new ValueBoolean("pause_gui_in_sp", true);
    public static ValueBoolean renderBodyPartAxis = new ValueBoolean("render_bodypart_axis", true);
    public static ValueInt maxRecentMorphs = new ValueInt("max_recent_morphs", 20, 1, 200);
    public static ValueBoolean allowMorphingIntoCategoryMorphs = new ValueBoolean("allow_morphing_into_category_morphs", false);
    public static ValueBoolean loadEntityMorphs = new ValueBoolean("load_entity_morphs", true);

    /**
     * Legacy {@code Metamorph.onConfigRegister} — registers the metamorph
     * config tree (and the {@code entity_selectors} OP-access toggle) with the
     * exact legacy ids/defaults/categories/markings. Subscribed from
     * {@code Blockbuster.registerConfigs()} (after McLib's and Aperture's
     * modules, before Blockbuster's own).
     */
    public static void onConfigRegister(RegisterConfigEvent event)
    {
        opEntitySelector = event.opAccess.category(MOD_ID).getBoolean("entity_selectors", true);
        opEntitySelector.syncable();

        ConfigBuilder builder = event.createBuilder(MOD_ID);

        preventGhosts = builder.category("acquiring").getBoolean("prevent_ghosts", true);
        preventKillAcquire = builder.getBoolean("prevent_kill_acquire", false);
        acquireImmediately = builder.getBoolean("acquire_immediately", false);

        keepMorphs = builder.category("morphs").getBoolean("keep_morphs", true);
        disablePov = builder.getBoolean("disable_pov", false);
        disableHealth = builder.getBoolean("disable_health", false);
        disableMorphAnimation = builder.getBoolean("disable_morph_animation", false);
        disableMorphDisguise = builder.getBoolean("disable_morph_disguise", false);
        disableFirstPersonHand = builder.getBoolean("disable_first_person_hand", false);
        disableFirstPersonHand.clientSide();
        morphInTightSpaces = builder.getBoolean("morph_in_tight_spaces", false);
        showMorphIdleSounds = builder.getBoolean("show_morph_idle_sounds", true);
        pauseGUIInSP = builder.getBoolean("pause_gui_in_sp", true);
        pauseGUIInSP.clientSide();
        renderBodyPartAxis = builder.getBoolean("render_bodypart_axis", true);
        renderBodyPartAxis.clientSide();
        maxRecentMorphs = builder.getInt("max_recent_morphs", 20, 1, 200);
        maxRecentMorphs.clientSide();
        allowMorphingIntoCategoryMorphs = builder.getBoolean("allow_morphing_into_category_morphs", false);
        loadEntityMorphs = builder.getBoolean("load_entity_morphs", true);
        loadEntityMorphs.clientSide();
    }

    /**
     * Legacy's dev-only diagnostic gate (S22 P252).
     *
     * <p>Metamorph 1.4 guarded its loudest self-checks with
     * {@code FMLForgePlugin.RUNTIME_DEOBF} — {@code true} in a shipped
     * (obfuscated) game, {@code false} in a ForgeGradle workspace, i.e.
     * {@code !RUNTIME_DEOBF} reads "developer mode". Two sites used it:
     * {@code AbstractMorph.initializeSettings}' "needSettingsUpdate was not set
     * to true…" error and {@code forceSettings}' null-settings NPE. Neither
     * ever fired for a player; both were assertions aimed at modders.</p>
     *
     * <p>The Fabric equivalent is {@code isDevelopmentEnvironment()} (true in a
     * Loom run / the JUnit suite, false in the packaged jar). A supplier so the
     * headless suite can exercise both branches; wired to the real loader at
     * its declaration (CROSS_CUTTING.md §1.9 — a real delegate, not a hole).</p>
     */
    public static BooleanSupplier developmentEnvironment =
        () -> FabricLoader.getInstance().isDevelopmentEnvironment();

    public static boolean DEBUG = false;

    public static void log(String message)
    {
        if (DEBUG)
        {
            LOGGER.info(message);
        }
    }
}
