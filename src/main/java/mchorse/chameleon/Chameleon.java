package mchorse.chameleon;

import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.lib.MolangHelper;
import mchorse.chameleon.mclib.ChameleonTree;
import mchorse.chameleon.mclib.ValueButtons;
import mchorse.mclib.config.ConfigBuilder;
import mchorse.mclib.events.RegisterConfigEvent;
import mchorse.mclib.math.Variable;
import mchorse.mclib.math.molang.MolangParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bundled Chameleon static holder — the port of Chameleon 1.2.2's {@code @Mod}
 * class <b>and</b> of the model-registry half of its {@code ClientProxy}
 * (roadmap: Chameleon port).
 *
 * <p>Chameleon adds Bedrock ({@code .geo.json} + {@code .animation.json}) models
 * as a Metamorph morph type. Its 1.12.2 distribution was a separate mod
 * depending on McLib and Metamorph; here it is bundled inside this jar like
 * McLib/Metamorph/Aperture, keeping its original {@code mchorse.chameleon.*}
 * package names for diff-ability.</p>
 *
 * <h3>Where the proxies went</h3>
 *
 * <p>Legacy had a {@code @SidedProxy} pair whose only real content was: a
 * {@code MolangParser} with Chameleon's extra query variables, the
 * {@code Map<String, ChameleonModel>} of loaded models, the models folder, and
 * {@code reloadModels()}/{@code getModelKeys()} — which {@code ChameleonSection}
 * called through {@code Chameleon.proxy}. The port
 * does not revive {@code @SidedProxy}: the state that has no client types lives
 * here, in the common source set, and the <b>loading</b> half (which walks the
 * config folder and needs a resource pack + file tree) is installed by
 * {@code ChameleonClient} into {@link #modelReloader}.</p>
 *
 * <p>That mirrors 1.12.2's behaviour exactly. Legacy's server-side
 * {@code CommonProxy.reloadModels()} was an empty method and
 * {@code getModelKeys()} returned an empty list, because Chameleon models are
 * only ever <i>rendered</i>; here the reloader is simply never installed on a
 * dedicated server, so {@link #MODELS} stays empty and every lookup misses —
 * the same outcome, with one fewer class.</p>
 *
 * <p><b>The mod id is {@code chameleon_morph}, not {@code chameleon}</b> — a
 * legacy collision workaround (an unrelated, more popular "Chameleon" mod owned
 * that id on 1.12.2). It is preserved because it is the config-folder and
 * config-file name: {@code config/chameleon/…} keeps working only because the
 * builder id below is what names the module.</p>
 *
 * Legacy source: chameleon/src/main/java/mchorse/chameleon/Chameleon.java
 * (+ {@code ClientProxy}/{@code CommonProxy})
 */
public class Chameleon
{
    /* Sadly "chameleon" mod ID conflicts with another popular mod... */
    public static final String MOD_ID = "chameleon_morph";
    public static final String MODNAME = "Chameleon";
    public static final String VERSION = "1.2.2";

    public static Logger LOGGER = LoggerFactory.getLogger("Chameleon");

    /**
     * The prefix every Chameleon morph name carries; the part after it is the
     * model folder key. Both {@code ChameleonFactory.hasMorph} and
     * {@code ChameleonMorph.getKey} are defined in terms of it.
     */
    public static final String MORPH_PREFIX = "chameleon.";

    /**
     * The shared Molang parser every {@code .animation.json} is parsed against
     * and every animation is evaluated with — legacy {@code ClientProxy.parser}.
     *
     * <p>Single instance on purpose: {@code MolangExpression}s parsed by it hold
     * references to <i>its</i> {@link Variable} objects, so
     * {@link MolangHelper#setMolangVariables} setting a value here is what makes
     * every already-parsed keyframe expression see the new frame. Parsing an
     * animation against one parser and evaluating it against another would
     * silently freeze every query at 0.</p>
     */
    public static final MolangParser PARSER;

    /**
     * Loaded models by folder key ({@code "wolf"}, {@code "packs/wolf"}) —
     * legacy {@code ClientProxy.chameleonModels}. Written only by the reloader
     * installed in {@link #modelReloader}; empty on a dedicated server.
     *
     * <p>Insertion-ordered so {@link #getModelKeys()} (and therefore the creative
     * picker) is stable across reloads rather than hash-ordered.</p>
     */
    public static final Map<String, ChameleonModel> MODELS = new LinkedHashMap<String, ChameleonModel>();

    /**
     * The client-side folder scan + parse (legacy {@code ClientProxy.reloadModels}).
     * Installed by {@code ChameleonClient}; a no-op until then, which is what
     * makes {@link #reloadModels()} safe to call from the common-side
     * {@code ChameleonSection.update}.
     */
    public static Runnable modelReloader = () -> {};

    /**
     * The skin file tree rooted at the models folder — legacy
     * {@code ClientProxy.tree}. Built and registered into
     * {@code GlobalTree.TREE} by {@code ChameleonClient}; {@code null} on a
     * dedicated server, and every reader null-guards.
     */
    public static ChameleonTree tree;

    /**
     * The models folder ({@code config/chameleon/models}) — legacy
     * {@code ClientProxy.modelsFile}. Set alongside {@link #tree}.
     */
    public static File modelsFile;

    static
    {
        PARSER = new MolangParser();

        MolangHelper.registerVars(PARSER);

        /* Additional Chameleon specific variables */
        PARSER.register(new Variable("query.head_yaw", 0));
        PARSER.register(new Variable("query.head_pitch", 0));

        PARSER.register(new Variable("query.velocity", 0));
        PARSER.register(new Variable("query.limb_swing", 0));
        PARSER.register(new Variable("query.limb_swing_amount", 0));
        PARSER.register(new Variable("query.age", 0));
    }

    /**
     * Rescan the models folder — legacy {@code Chameleon.proxy.reloadModels()},
     * called by {@code ChameleonSection.update} every time a morph picker opens.
     */
    public static void reloadModels()
    {
        modelReloader.run();
    }

    /**
     * Legacy {@code Chameleon.proxy.getModelKeys()}.
     */
    public static Collection<String> getModelKeys()
    {
        return MODELS.isEmpty() ? Collections.emptyList() : MODELS.keySet();
    }

    /**
     * Resolve a loaded model by folder key, or {@code null}. Never an error —
     * a morph whose model is missing renders nothing, exactly as legacy.
     */
    public static ChameleonModel getModel(String key)
    {
        return MODELS.get(key);
    }

    /**
     * Legacy {@code Chameleon.onConfig(RegisterConfigEvent)}: one module,
     * {@code chameleon}, one category, {@code general}, holding only the
     * client-side button row.
     *
     * <p>The module id is the bare {@code "chameleon"}, not {@link #MOD_ID} —
     * that is what puts the file at {@code config/chameleon/config.json} and
     * what the {@code chameleon.config.*} lang keys are built from. (The module
     * id names the <i>folder</i>; the file inside it is always
     * {@code config.json}, exactly as {@code config/blockbuster/config.json}.)</p>
     */
    public static void onConfigRegister(RegisterConfigEvent event)
    {
        ConfigBuilder builder = event.createBuilder("chameleon");

        /* General */
        builder.category("general").register(new ValueButtons("buttons").clientSide());
    }
}
