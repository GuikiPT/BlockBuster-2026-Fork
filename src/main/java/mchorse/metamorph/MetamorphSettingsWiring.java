package mchorse.metamorph;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.RegisterHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * S22 P222 — installs the Metamorph settings/blacklist/remap registry.
 *
 * <p>{@link RegisterHandler} shipped with P49 but nothing ever called
 * {@code register()}, so the three collector events had no Metamorph
 * subscriber, {@code assets/metamorph/morphs.json} and
 * {@code config/metamorph/morphs.json} were never read, and
 * {@link MorphManager#activeSettings}/{@code activeMap} stayed empty for the
 * whole session. Everything downstream of them was dark: the 15 abilities, 12
 * actions and 4 attacks were unreachable, per-morph health/hostility/speed
 * never applied, the legacy morph-id remapper never ran, and
 * {@code MorphHandler.playerLogsIn} shipped an <b>empty</b>
 * {@code PacketSettings}/{@code PacketBlacklist} to every joining client.</p>
 *
 * <h2>What legacy did, and where</h2>
 *
 * <p>Two separate legacy call sites, both reproduced here:</p>
 *
 * <ol>
 *   <li>{@code CommonProxy.load()} (FML init) subscribed
 *       {@code new RegisterHandler()} onto the Forge bus and generated the
 *       three empty user config files ({@code morphs.json} = {@code {}},
 *       {@code blacklist.json} = {@code []}, {@code remap.json} = {@code {}}).
 *       That is {@link #install()}'s first half.</li>
 *   <li>{@code Metamorph.serverStarting} (FMLServerStartingEvent) then fired
 *       the three collector events and swapped the results into the live
 *       manager:
 *       <pre>
 *       MorphManager.INSTANCE.setActiveBlacklist(null, MorphUtils.reloadBlacklist());
 *       MorphManager.INSTANCE.setActiveSettings(MorphUtils.reloadMorphSettings());
 *       MorphManager.INSTANCE.setActiveMap(MorphUtils.reloadRemapper());
 *       </pre>
 *       That is {@link #reload()}, hooked onto
 *       {@link ServerLifecycleEvents#SERVER_STARTING} — the Fabric event that
 *       matches {@code FMLServerStartingEvent} (it is also where the port
 *       already captures the running server for the dispatcher and registers
 *       commands).</li>
 * </ol>
 *
 * <p>The audit only named the missing {@code register()}; the server-start
 * half was missing too, and wiring only the first would still have left
 * {@code activeSettings} empty. Both are here.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>{@link #install()} must run after the morph factories have been
 * registered <b>and</b> after {@code MorphManager.register()} has seeded
 * {@code abilities}/{@code actions}/{@code attacks}, because
 * {@code MorphSettingsAdapter} resolves the {@code "abilities"}/{@code "action"}/
 * {@code "attack"} id strings against those maps at <i>parse</i> time — an id
 * that is not registered yet is silently dropped. It does: the single call site
 * is the tail of Blockbuster's initializer, right after
 * {@code registerBlockbusterMorphs()}. The parse itself happens later still,
 * at server start, exactly like 1.12.2.</p>
 *
 * <p>Subscription order relative to Blockbuster's own
 * {@code blockbuster_pack.MetamorphHandler} is immaterial — every collector
 * contributes into one shared {@code Set}/{@code Map} — so the fact that
 * legacy's mod-load order subscribed Metamorph's handler first is not a
 * behaviour this has to reproduce.</p>
 */
public final class MetamorphSettingsWiring
{
    /**
     * The installed handler — {@code null} until {@link #install()} runs. Kept
     * public so the file locations it resolved ({@code config/metamorph/…}) can
     * be inspected and, in headless tests, redirected.
     */
    public static RegisterHandler handler;

    private static boolean installed;

    private MetamorphSettingsWiring()
    {}

    /**
     * Subscribe the settings/blacklist/remap collectors and arm the
     * server-start reload. Idempotent — the Fabric events it registers on
     * cannot be unsubscribed, so a second call must not double-subscribe.
     */
    public static synchronized void install()
    {
        if (installed)
        {
            return;
        }

        installed = true;

        RegisterHandler registerHandler = new RegisterHandler();

        /* Legacy CommonProxy.load(): the three user config files are generated
         * empty on first run so a user has something to edit. */
        registerHandler.setupFiles();
        registerHandler.register();

        handler = registerHandler;

        /* Legacy Metamorph.serverStarting. */
        ServerLifecycleEvents.SERVER_STARTING.register(server -> reload());
    }

    /**
     * Fire the three collector events and swap the results into the live
     * {@link MorphManager} — the body of legacy {@code Metamorph.serverStarting}.
     *
     * <p>Public because it is also the honest "reload everything" entry point;
     * {@code /metamorph reload <type>} does the per-type version of this and
     * additionally broadcasts the result to the connected clients.</p>
     */
    public static void reload()
    {
        /* Legacy passed a null world here (the blacklist swap does not need
         * one); kept verbatim. */
        MorphManager.INSTANCE.setActiveBlacklist(null, MorphUtils.reloadBlacklist());
        MorphManager.INSTANCE.setActiveSettings(MorphUtils.reloadMorphSettings());
        MorphManager.INSTANCE.setActiveMap(MorphUtils.reloadRemapper());
    }

    /**
     * Whether {@link #install()} has run. Test seam for the regression pin.
     */
    public static boolean isInstalled()
    {
        return installed;
    }
}
