package mchorse.blockbuster;

import java.util.List;
import mchorse.aperture.client.ApertureClient;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.aperture.CameraHandlerClient;
import mchorse.blockbuster.api.ModelClientHandler;
import mchorse.blockbuster.audio.AudioLibrary;
import mchorse.blockbuster.audio.AudioRenderer;
import mchorse.blockbuster.client.LimbRollWiring;
import mchorse.blockbuster.client.RenderingHandler;
import mchorse.blockbuster.client.SkinHandler;
import mchorse.blockbuster.client.UnsentPacketWiring;
import mchorse.blockbuster.client.commands.ItemNBTCommand;
import mchorse.blockbuster.client.commands.ModelCommands;
import mchorse.blockbuster.client.compat.iris.IrisPbrGifBridge;
import mchorse.blockbuster.client.gui.GuiHandlerClient;
import mchorse.blockbuster.client.gui.GuiRecordingOverlay;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.RecordingEditorRefresh;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.client.particles.ParticleLibraryWiring;
import mchorse.blockbuster.client.render.GunMiscRender;
import mchorse.blockbuster.client.render.RenderActor;
import mchorse.blockbuster.client.render.RenderGunProjectile;
import mchorse.blockbuster.client.render.RenderLastPass;
import mchorse.blockbuster.client.render.WorldDebugRenderer;
import mchorse.blockbuster.client.render.item.BlockbusterItemRenderers;
import mchorse.blockbuster.client.render.item.TileEntityGunItemStackRenderer;
import mchorse.blockbuster.client.render.tileentity.ModelBlockRenderWiring;
import mchorse.blockbuster.common.block.BlockGreen;
import mchorse.blockbuster.common.block.ChromaColor;
import mchorse.blockbuster.client.render.tileentity.TileEntityDirectorRenderer;
import mchorse.blockbuster.client.render.tileentity.TileEntityModelItemStackRenderer;
import mchorse.blockbuster.client.render.tileentity.TileEntityModelRenderer;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.blockbuster.client.textures.SkinPipelineWiring;
import mchorse.blockbuster.client.textures.Textures;
import mchorse.blockbuster.client.video.ScreenshotKeyHandler;
import mchorse.blockbuster.client.watchdog.SkinWatcherClient;
import mchorse.blockbuster.client.watchdog.TextureReloaderWiring;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.events.PlayerHandler;
import mchorse.blockbuster.events.TickHandler;
import mchorse.blockbuster.network.ClientNetworkState;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.ResourcePackUtils;
import mchorse.blockbuster.utils.mclib.BlockbusterConfigButtons;
import mchorse.blockbuster.utils.mclib.BlockbusterJarTree;
import mchorse.blockbuster.utils.mclib.BlockbusterTree;
import mchorse.blockbuster.utils.mclib.ImageFolder;
import mchorse.blockbuster.utils.mclib.ValueAudioButtons;
import mchorse.blockbuster.utils.mclib.ValueMainButtons;
import mchorse.blockbuster_pack.client.BlockbusterSectionSkins;
import mchorse.blockbuster_pack.client.RecordMorphClient;
import mchorse.blockbuster_pack.client.gui.BlockbusterMorphEditors;
import mchorse.blockbuster_pack.client.render.BlockbusterMorphRenderers;
import mchorse.blockbuster_pack.morphs.structure.StructureRenderers;
import mchorse.blockbuster_pack.trackers.MorphTracker;
import mchorse.chameleon.client.ChameleonClient;
import mchorse.mclib.McLib;
import mchorse.mclib.client.InputRenderer;
import mchorse.mclib.client.KeyboardHandler;
import mchorse.mclib.client.McLibClientSeams;
import mchorse.mclib.client.gui.framework.elements.GuiModelRenderer;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.keys.LangKey;
import mchorse.mclib.config.Config;
import mchorse.mclib.config.values.Value;
import mchorse.mclib.config.values.ValueGUI;
import mchorse.mclib.network.ChannelLedger;
import mchorse.mclib.network.ClientDispatcherHooks;
import mchorse.mclib.network.Side;
import mchorse.mclib.network.mclib.client.AbstractClientHandlerAnswer;
import mchorse.mclib.network.mclib.client.ConfirmScreenWiring;
import mchorse.mclib.utils.DummyEntity;
import mchorse.mclib.utils.NextTickQueue;
import mchorse.mclib.utils.OpHelper;
import mchorse.mclib.utils.OptifineHelper;
import mchorse.mclib.utils.files.FileTree;
import mchorse.mclib.utils.files.GlobalTree;
import mchorse.metamorph.MetamorphCommon;
import mchorse.metamorph.bodypart.BodyPartRenderer;
import mchorse.metamorph.client.MetamorphClient;
import mchorse.metamorph.client.render.RenderMorph;
import mchorse.metamorph.network.Dispatcher;
import mchorse.vanilla_pack.editors.MetamorphMorphEditors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Client-side entrypoint of the Blockbuster mod
 */
public class BlockbusterClient implements ClientModInitializer
{
    /** Bundled McLib keyboard handler (dashboard keybind). */
    public static KeyboardHandler mclibKeys = new KeyboardHandler();

    /**
     * P44.2: bundled McLib tutorials overlay (mouse cursor, mouse buttons +
     * wheel, keystroke feed). Legacy {@code mclib ClientProxy.load} registered
     * {@code new InputRenderer()} on the Forge event bus.
     */
    public static InputRenderer inputRenderer = new InputRenderer();

    /**
     * P145: Blockbuster's global client keybind family (director play/pause/
     * record, open-gun, and the S17 zoom / gun-reload / gun-shoot bindings) plus
     * the disconnect cleanup handler.
     */
    public static mchorse.blockbuster.client.KeyboardHandler keys = new mchorse.blockbuster.client.KeyboardHandler();

    /** P197: gun zoom / crosshair render glue (registered in client init). */
    public GunMiscRender gunMiscRender = new GunMiscRender();

    /**
     * P204: one-key transparent still screenshot (world variant). Self-contained
     * binding + tick poll + {@code WorldRenderEvents.LAST} capture hook.
     *
     * <p>The model-editor variant has a <b>separate</b> trigger and is not wired
     * here: {@code GuiModelEditorPanel} registers an F2 keybind on the S3
     * framework's own {@code keys()} manager, and its {@code draw} calls
     * {@link mchorse.blockbuster.client.video.ScreenshotCapture#captureModel}.
     * (Until batch U-Q it had no trigger at all and this comment claimed
     * otherwise.)</p>
     */
    public static ScreenshotKeyHandler screenshotKeys = new ScreenshotKeyHandler();

    /**
     * P135: Blockbuster's dashboard panels holder (mirrors 1.12.2's
     * {@code ClientProxy.panels}); subscribes to the McLib dashboard
     * register/teardown lifecycle events.
     */
    public static GuiBlockbusterPanels panels = new GuiBlockbusterPanels();

    /**
     * P124: recording overlay HUD renderer (REC icon + caption). Reads its
     * display state from {@code ClientProxy.recordingOverlay}.
     */
    public static GuiRecordingOverlay recordingOverlay = new GuiRecordingOverlay();

    @Override
    public void onInitializeClient()
    {
        Blockbuster.LOGGER.info("Blockbuster (Fabric port) client initialized");

        /* P21: install the client half of OpHelper — the local player's op
         * level behind the common-side seam. Legacy read
         * EntityPlayerSP.getPermissionLevel() directly; yarn 1.20.4 keeps
         * ClientPlayerEntity.getPermissionLevel() protected (verified with
         * javap) and exposes only the public hasPermissionLevel(int)
         * predicate, so the exact level is recovered by probing downwards
         * from the vanilla maximum (4). This matters because legacy
         * getPlayerOpLevel() returns a *level*, not a boolean — a 0/2
         * collapse would be indistinguishable for today's
         * OpHelper.isOp(level) consumers but would silently diverge for any
         * future >2 gate. */
        OpHelper.setClientOpLevelProvider(() ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc == null || mc.player == null)
            {
                return 0;
            }

            return OpHelper.probeOpLevel(mc.player::hasPermissionLevel);
        });

        /* P217: the Iris-backed shadow-pass predicate behind the common-side
         * seam MorphTracker declares. MorphTracker lives in src/main (legacy
         * MorphTracker holds a TrackingPacket field, so it is common code) and
         * cannot see the client-only OptifineHelper; legacy skipped Aperture
         * morph tracking during an Optifine shadow pass, and this restores that
         * — the tracker would otherwise record one extra camera-frame sample
         * per shadow pass, duplicating positions in the exported tracking JSON. */
        MorphTracker.shadowPass =
            OptifineHelper::isOptifineShadowPass;

        /* P217.1: register the animated-GIF PBR companion-map loader with Iris.
         * A no-op without Iris (it does not even reach Class.forName), which is
         * the whole point — the S7 GIF pipeline behaves identically on a vanilla
         * install, exactly as legacy's Config.isShaders() gate did. */
        IrisPbrGifBridge.setup();

        /* P80 precursor: actors render as Steve until morph rendering lands */
        EntityRendererRegistry.register(Blockbuster.ACTOR, RenderActor::new);

        /* P80.3 (debug half): the world-debug render tail — legacy
         * RenderingHandler.onRenderLast's F3 record-path draw. RenderActor
         * registers the records it draws; this is what drains them, and it is
         * the only reader of the record_render_debug_paths config. */
        WorldDebugRenderer.install();

        /* P197: gun projectile renderer + gun render glue (zoom ramp, crosshair,
         * arm pose). The projectile renderer draws the projectile's morph with a
         * fade envelope; the misc render drives the zoom FOV/sensitivity ramp and
         * the custom crosshair. */
        EntityRendererRegistry.register(Blockbuster.GUN_PROJECTILE, RenderGunProjectile::new);

        /* Bundled Metamorph P56.1: the morph ghost (metamorph:morph) — the
         * spinning cyan pickup you walk into to acquire a killed mob's morph. */
        EntityRendererRegistry.register(MetamorphCommon.MORPH,
            RenderMorph::new);

        this.gunMiscRender.register();

        /* S22 P244: the two client-tick senders 1.12.2 had and the port never
         * wrote — gun shoot/reload input (GunShootHandler) and the client frame
         * recorder + damage-control probe (FrameHandler). */
        UnsentPacketWiring.install();

        /* P197: the client gun render-cache pump (legacy PlayerHandler.updateClient
         * gun half) — timer decrement / eviction / props.update() per client tick.
         * S22 P247 moved the registration into PlayerHandler.updateClient (duty 2)
         * so the five duties keep legacy's order; see registerPlayerTick(). */

        /* P197: install the client-only re-equip cache migration seam the
         * main-source ItemGun.allowNbtUpdateAnimation calls. */
        ItemGun.cacheMigrator =
            TileEntityGunItemStackRenderer::migrate;

        /* P83: builtin item renderers — the gun's DynamicItemRenderer. The 16
         * model-block light-variant items go through the same facade from
         * registerModelBlockRendering() below. Ports legacy ClientProxy.preLoad's
         * setTileEntityItemStackRenderer wiring. */
        BlockbusterItemRenderers.register();

        /* P84: install the DummyEntity factory the model / morph GUI viewports
         * ({@link GuiModelRenderer}) host. Legacy {@code new DummyEntity(mc.world)};
         * yarn's LivingEntity needs a type + world, so a client-world armor-stand
         * host is used (only its living-entity surface matters). World-less
         * screens (main menu) get null → the viewport renders with no host. */
        GuiModelRenderer.dummyEntityFactory = (mc) ->
            mc == null || mc.world == null ? null : new DummyEntity(EntityType.ARMOR_STAND, mc.world);

        /* P54: fill BodyPartManager's client-only per-part init seam, so a
         * morph's body parts get their dummy host entity the first time they
         * are rendered. Without it initBodyParts() is a no-op latch and every
         * !useTarget body part stays invisible. */
        BodyPartRenderer.install();

        /* P94 / P129: the director block-entity F3 debug-cube renderer (only
         * draws while the debug HUD is up). */
        BlockEntityRendererRegistry.register(
            Blockbuster.DIRECTOR_TILE, TileEntityDirectorRenderer::new);
        /* P96: model-block rendering — the BE renderer (drives the morph render)
         * and the model-block-as-item renderer. */
        this.registerModelBlockRendering();
        /* Chroma block items: one item model per color, off the BlockStateTag. */
        this.registerChromaItemModels();

        /* P54: Blockbuster's own pack-morph client renderers. Registered before
         * MetamorphClient.init() installs the dispatcher, though the registry
         * invalidates its resolution cache on every registration, so neither
         * order can leave a stale "no renderer" answer behind. */
        BlockbusterMorphRenderers.register();

        /* Bundled Metamorph (S4/P54.1): entity-selector render substitution —
         * create the model handler and load config/metamorph/selectors.json. */
        MetamorphClient.init();

        /* S22 P247: the client player-tick loop (legacy PlayerTickEvent, client
         * side). Registered *after* MetamorphClient.init() on purpose — that
         * call installs the P225 squid-air HUD mirror on END_CLIENT_TICK, and
         * legacy read the capability into the HUD before capability.update()
         * drained it in the same tick. */
        this.registerPlayerTick();

        /* P165-registry: register the morph-editor factories into the client
         * MorphEditorRegistry — the editor-discovery path the morph editor /
         * creative picker (SEAM P58) reads. These are the client halves of
         * legacy MetamorphFactory/BlockbusterFactory.registerMorphEditors.
         *
         * ORDER IS BEHAVIOUR: the registry walks factories in reverse
         * registration order, and legacy registered Metamorph's factory before
         * Blockbuster's — so Blockbuster's editors are offered first and
         * Metamorph's bare GuiAbstractMorph catch-all stays dead last. */
        MetamorphMorphEditors.register();
        BlockbusterMorphEditors.register();

        /* Bundled Chameleon: models folder scan, the c.s skin pack + file tree,
         * the Molang client-context seam, and Chameleon's own morph renderer,
         * morph editor and config button row. Registered last, so its editor is
         * offered before Blockbuster's in the reverse-order walk — harmless
         * either way, since GuiChameleonMorph.canEdit only accepts a
         * ChameleonMorph and no other editor claims one. */
        ChameleonClient.init();

        this.registerNetworking();

        /* P100 + S22/P228: install the client screen-open routing (Forge
         * GuiHandler replacement) — one factory per legacy GUI id
         * (ACTOR/MODEL_BLOCK/PLAYBACK), each now opening its real screen. */
        GuiHandlerClient.install();

        /* S22/P228: point mclib's PacketConfirm handler at the real
         * GuiConfirmationScreen (it shipped with a record-only headless
         * default in P26 and was never wired to the P36 modal). */
        ConfirmScreenWiring.install();

        /* S22/P242: McLib's three unwritten client seams — the addon-facing
         * Icons name registry (legacy mclib CommonProxy.init), the texture
         * picker/multiskin-editor bind+size binder P40 left for S7, and the
         * dashboard sink for a server-pushed config module. */
        McLibClientSeams.install();

        /* S13 P154 / S22 batch U-K: build the Snowstorm preset library (legacy
         * CommonProxy line 124) and hand the common-side creative section its
         * reload seam. Until this landed ClientProxy.particles had no production
         * writer at all, so every Snowstorm morph resolved a null scheme and
         * rendered nothing. Must precede the JOIN hook below, which reloads it. */
        ParticleLibraryWiring.install();

        /* P69: the client uses a ModelClientHandler (client-flag morphs + S6 GL
         * compile) and reloads local models when joining any server/world —
         * legacy ModelHandler.onClientConnect(ClientConnectedToServerEvent),
         * which called proxy.loadModels(false) and particles.reload(). */
        CommonProxy.models = new ModelClientHandler();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onClientJoin());

        /* P88: first-run skin folders + guaranteed image-morph default skin,
         * and the 30-tick general-skins-folder auto-sorter. */
        this.registerSkins();

        /* P88.1: the two picker file trees (disk skins + the mod's own PNGs). */
        this.registerFileTrees();

        mclibKeys.register();

        /* P44.2: the tutorials overlay — draws over any open screen (legacy
         * DrawScreenEvent.Post), feeds the keystroke list from screen key
         * presses and the scroll accumulator from screen scroll events. */
        inputRenderer.register();

        /* P145: Blockbuster's own global keybinds + disconnect cleanup. */
        keys.register();

        /* P204: one-key transparent still screenshot (world variant). */
        screenshotKeys.register();

        /* P135: register Blockbuster's six dashboard panels against the
         * bundled McLib dashboard lifecycle (RegisterDashboardPanels fires
         * when GuiDashboard is built; RemoveDashboardPanels fires from the
         * KeyboardHandler world-exit path). */
        panels.register();

        /* Bundled Aperture (S15): camera seams, client registries, ticks */
        ApertureClient.init();

        /* P185.1: Blockbuster's camera-editor integration — scene sync on
         * scrub/play/rewind, the embedded recording editor, the director
         * config section (legacy CameraHandler.registerClient). */
        CameraHandlerClient.register();

        /* S22 P238: the local-player lookup EntityUtils.getRoll and
         * Record.applyFrameClient need (limbs flagged "roll" were upright
         * during playback), plus the recorded sneak movement input. */
        LimbRollWiring.install();

        /* P73: the client-side /model command family (reload/report/clear/
         * combine now; export/export_obj/convert/clear_structures in P74).
         * Legacy CommandModel was a client command (ClientCommandHandler). */
        ModelCommands.register();

        /* P162: install the client structure-morph seam (STRUCTURES cache +
         * render/request/reload). This also registers the StructureReloader
         * handler that /model clear_structures (P74) invokes. */
        StructureRenderers.install();

        /* S22 P235: point RecordMorph's two client seams (record-cache lookup +
         * record-request RPC) at the real client implementations. P161 shipped
         * them unassigned, so a record morph never resolved its record. */
        RecordMorphClient.install();

        /* S22 P236: route the three record packet handlers' "refresh the open
         * recording editor" call at the real dashboard panel — they shipped with
         * the call replaced by a TODO, so the editor showed stale actions. */
        RecordingEditorRefresh.install();

        /* S19 P207 sweep: client-side /item_nbt (permission-free clipboard dump
         * of the held stack; 1.12-format /give string on the boolean arg).
         * Legacy CommandItemNBT was also a ClientCommandHandler command. */
        ItemNBTCommand.register();

        /* S7/P91: multiskin GL-upload seam, MULTISKIN_PROCESSED consumer, and
         * the multiskin-clear resource-reload listener */
        Textures.init();

        /* S22 P231/P232: the three ActorsPack seams P89/P90 left unassigned —
         * animated-GIF virtual frames + their process scheduling, the URL-skin
         * resolver, and GifTexture's animation clock. Without this line a .gif
         * skin is a missing texture and a URL skin never downloads. */
        SkinPipelineWiring.install();

        /* P39: GLFW-backed raw-key poller for combo keybind modifier checks
         * (Keys.keyDownPoller stays a no-op headless) */
        GuiUtils.installKeyPoller();

        /* P79: tick the extruded-layer image cache (timer decrement + eviction)
         * once per client tick, as the legacy ClientProxy did. */
        ClientTickEvents.END_CLIENT_TICK.register(client ->
            ModelExtrudedLayer.tickCache());

        /* P149: Snowstorm emitter collection drivers — tick advances every live
         * Bedrock emitter's simulation; disconnect flushes them. The render pass
         * (and its sanityTicks auto-kill) is wired later in P153.
         * S22 P247: the tick half moved into PlayerHandler.updateClient (duty 4),
         * which is where legacy called updateEmitters and which also restores
         * legacy's "local player in world" gate. */
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            RenderingHandler.resetEmitters());

        /* P188.1/P191: instantiate the client-side .wav library so PacketAudio
         * receipt (ClientHandlerAudio -> ClientProxy.audio.handleAudio) drives a
         * real AudioLibrary instead of NPEing on a null holder. Legacy
         * ClientProxy.load: audio = new AudioLibrary(new File(configFile, "audio")).
         * Constructing the library only mkdirs()'s the folder — no AL device is
         * touched until the first packet lazily loads + plays a wav. The reset()
         * lifecycle hooks (dashboard close, reset_audio button, config buttons)
         * were already wired behind null-guards and now become live. */
        ClientProxy.audio = new AudioLibrary(
            BlockbusterPaths.audio().toFile());

        this.registerAudioLifecycle();

        this.registerAudioOverlay();

        this.registerRecordingOverlay();

        /* S22/P233: fill the four TextureReloader handler slots P92 shipped as
         * no-ops (texture/GIF/multiskin/whole-pack). Unconditional — the
         * dashboard + /model reload paths use them regardless of watch_files. */
        TextureReloaderWiring.install();

        /* P92: optional live-reload file watcher. No-op unless the new
         * general.watch_files config is enabled (default off — parity first);
         * legacy polling loops are untouched either way. */
        SkinWatcherClient.start();

        /* P210: the client-source ValueGUI action rows (general.buttons /
         * audio.buttons). */
        registerConfigButtons();

        /* P39/P22: legacy ClientProxy.init's last line — put the KeybindConfig
         * into the live ConfigManager's modules so the keybinds module appears
         * in the config panel and GUI rebinding is reachable. Runs after the
         * main entrypoint's ConfigManager.register(...), exactly like legacy
         * (super.init() -> configs.modules.put(keybinds.id, keybinds)). */
        mchorse.mclib.ClientProxy.init();
    }

    /**
     * P69 + S13 P154 — the body of legacy
     * {@code ModelHandler.onClientConnect(ClientConnectedToServerEvent)}
     * (1.12.2 lines 104–105), fired once per world/server join:
     *
     * <pre>Blockbuster.proxy.loadModels(false);
     *Blockbuster.proxy.particles.reload();</pre>
     *
     * <p><b>Order is behaviour</b> and both halves are required. The port shipped
     * only the first line, which left the Bedrock preset map permanently empty:
     * {@link mchorse.blockbuster.client.particles.BedrockLibrary}'s constructor
     * fills just the {@code factory} map, and {@code reload()} is what copies the
     * factory presets into {@code presets} <i>and</i> scans the user folder —
     * i.e. the very map {@code SnowstormClient.getScheme} reads. A named method
     * (rather than an inline lambda) so the ordering is pinned in bytecode by
     * {@code ParticleLibraryWiringTest}.</p>
     */
    static void onClientJoin()
    {
        CommonProxy.loadModels(false);
        ParticleLibraryWiring.reload();
    }

    /**
     * P210: contribute the client-only {@code ValueGUI} action rows to the
     * config panel — {@link mchorse.blockbuster.utils.mclib.ValueMainButtons}
     * (general category) and {@link mchorse.blockbuster.utils.mclib.ValueAudioButtons}
     * (audio category). Legacy {@code Blockbuster.onConfigRegister} registered
     * these as the first entry of each category
     * ({@code builder.category("general").register(new ValueMainButtons("buttons").clientSide())}).
     *
     * <p>batch-4 integration (P210/P208): {@code Blockbuster.onConfigRegister}
     * lives in the <b>main</b> source set and cannot reference these
     * <b>client</b>-source {@code ValueGUI} subclasses (they implement the
     * client-only {@code IConfigGuiProvider}); the module is also built during
     * the main entrypoint, before this client entrypoint runs. So the port wires
     * them here, after {@code ConfigManager.register(...)} has populated the
     * blockbuster module, injecting each as the <em>first</em> sub-value of its
     * category to preserve the legacy position ({@code general.buttons} /
     * {@code audio.buttons} lead their categories — fixture parity). Idempotent:
     * a {@code buttons} sub-value already present (e.g. once P208 registers them
     * via a main-source seam) makes this a no-op.</p>
     */
    public static void registerConfigButtons()
    {
        /* batch-4 integration (P208/P210): the button VALUE nodes are the
         * main-source ValueMainButtons/ValueAudioButtons (registered first in
         * each category by Blockbuster.onConfigRegister, for server config
         * parity); their client-only widget rows are contributed through the
         * ConfigGuiProviders factory registry, keyed on those classes. */
        BlockbusterConfigButtons.register();

        Config module = McLib.proxy.configs.modules.get(Blockbuster.MOD_ID);

        if (module == null)
        {
            return;
        }

        injectButtonsFirst(module, "general", new ValueMainButtons("buttons"));
        injectButtonsFirst(module, "audio", new ValueAudioButtons("buttons"));
    }

    /**
     * Inserts {@code buttons} as the first sub-value of the named category
     * (creating the category if the module lacks it), preserving the order of
     * any existing entries. No-op when a {@code buttons} entry is already there.
     */
    private static void injectButtonsFirst(Config module, String category, ValueGUI buttons)
    {
        Value cat = module.values.get(category);

        if (cat == null)
        {
            cat = new Value(category);
            cat.setConfig(module);
            module.values.put(category, cat);
        }

        if (cat.getSubValue(buttons.id) != null)
        {
            return;
        }

        List<Value> existing = cat.getSubValues();

        cat.removeAllSubValues();
        buttons.clientSide();
        buttons.setConfig(module);
        cat.addSubValue(buttons);

        for (Value value : existing)
        {
            cat.addSubValue(value);
        }
    }

    /**
     * P96: register the model-block block-entity renderer and the model-block
     * item ("TEISR") renderer.
     *
     * <p>The BE renderer factory stashes the created instance in
     * {@code TileEntityModelItemStackRenderer.modelRenderer} so the item renderer
     * reuses the same instance (legacy {@code ClientProxy.modelRenderer}). The
     * item renderer is the single shared {@code BlockbusterItemRenderers.MODEL},
     * registered for all 16 light-variant model items ({@code model},
     * {@code model1}..{@code model15}); this scans the registry defensively and
     * silently skips ids that are not registered. The per-client-tick cache sweep
     * (legacy {@code RenderingHandler}) evicts stale cached models.</p>
     *
     * <p>P229 also installs the render seams here — before this, every one of
     * them was null / inert and the block drew nothing at all.</p>
     */
    /**
     * Make the chroma block <b>items</b> render in their own color.
     *
     * <p>1.12 gave each color its own metadata sub-item, so each got its own
     * item model for free. On 1.20.4 the color rides the {@code BlockStateTag}
     * NBT ({@link BlockGreen#colorStack}) — vanilla honors that on placement, so
     * <i>placed</i> blocks always picked the right blockstate variant, but item
     * rendering never looks at it: one item id resolves to exactly one
     * {@code models/item/<id>.json}. Both chroma item models parent to
     * {@code block/green}, so all 16 creative-tab stacks drew green.</p>
     *
     * <p>This is the vanilla way to vary an item model off stack contents (the
     * same mechanism bows and compasses use): a {@code blockbuster:chroma}
     * predicate reporting the stack's color, and one override per color in the
     * two item models. Values are {@code ordinal / 8} so every threshold is an
     * exact binary fraction and the {@code >=} the override list matches with
     * cannot be tripped by float drift.</p>
     */
    private void registerChromaItemModels()
    {
        Identifier id = new Identifier(Blockbuster.MOD_ID, "chroma");

        registerChromaPredicate(Blockbuster.greenBlock, id);
        registerChromaPredicate(Blockbuster.dimGreenBlock, id);
    }

    private static void registerChromaPredicate(Block block, Identifier id)
    {
        if (block == null)
        {
            return;
        }

        Item item = block.asItem();

        if (item == Items.AIR)
        {
            return;
        }

        ModelPredicateProviderRegistry.register(item, id,
            (stack, world, entity, seed) -> ChromaColor.fromStack(stack).ordinal() / 8F);
    }

    private void registerModelBlockRendering()
    {
        BlockEntityRendererRegistry.register(
            Blockbuster.MODEL_BLOCK_TILE,
            context -> TileEntityModelItemStackRenderer.modelRenderer =
                new TileEntityModelRenderer(context));

        for (int i = 0; i < 16; i++)
        {
            String name = i == 0 ? "model" : "model" + i;
            Identifier id = new Identifier(Blockbuster.MOD_ID, name);
            Item item = Registries.ITEM.get(id);

            if (item != Items.AIR)
            {
                BlockbusterItemRenderers.registerModelBlockItem(item);
            }
        }

        /* P229: fill the model block's five render seams (morph draw, entity-morph
         * override, shadow, and the item renderer's live/static drawers). */
        ModelBlockRenderWiring.install();

        /* P80.3: the sorted render-last tail pass — registers the drain on
         * WorldRenderEvents.BEFORE_DEBUG_RENDER and fills RenderingHandler's
         * draw + alive-actor seams. Without this the three shipped "render last"
         * switches (Replay.renderLast, TileEntityModelSettings.renderLast, and
         * the two GUI toggles that write them) are read by nothing. */
        RenderLastPass.install();

        /* S22 P247: the model-block item cache pump moved into
         * PlayerHandler.updateClient (duty 1, legacy's first statement). */
    }

    /**
     * P188.1: the two client audio lifecycle couplings that keep our own
     * OpenAL sources coherent with the game.
     *
     * <ul>
     *   <li><b>Game-pause bridging.</b> Legacy edge-detected
     *       {@code mc.isGamePaused()} inside {@code RenderingHandler.onRenderLast}
     *       and called {@code ClientProxy.audio.pause(isPaused)} only on change.
     *       On 1.20.4 the pause signal is the client's own state — yarn
     *       {@code MinecraftClient.isPaused()} (verified with javap) — and the
     *       cheapest faithful carrier is {@code ClientTickEvents.END_CLIENT_TICK}
     *       (the plan's seam choice: only the edge matters, and AL plays on
     *       between frames regardless). The edge state + fan-out live in
     *       {@link mchorse.blockbuster.client.RenderingHandler#updateAudioPause(boolean)},
     *       the port of the legacy class that owned {@code wasPaused}.</li>
     *   <li><b>Resource-reload reset.</b> Legacy registered one reload listener
     *       that did {@code audio.reset()} unconditionally and
     *       {@code StructureMorph.reloadStructures()} behind a
     *       {@code player != null} guard; the port keeps both halves in one
     *       {@code SimpleSynchronousResourceReloadListener}
     *       ({@code blockbuster:audio_reset}) whose body is
     *       {@link ClientProxy#onResourceReload()}.</li>
     * </ul>
     *
     * <p>The third coupling — dashboard teardown — is already wired in
     * {@code GuiBlockbusterPanels.onUnregister} (P135).</p>
     */
    private void registerAudioLifecycle()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client ->
            RenderingHandler.updateAudioPause(client.isPaused()));

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new SimpleSynchronousResourceReloadListener()
            {
                @Override
                public Identifier getFabricId()
                {
                    return new Identifier(Blockbuster.MOD_ID, "audio_reset");
                }

                @Override
                public void reload(ResourceManager manager)
                {
                    ClientProxy.onResourceReload();
                }
            });
    }

    /**
     * P190: the in-game waveform HUD overlay (legacy
     * {@code RenderingHandler.onHUDRender}, {@code RenderGameOverlayEvent.Post}
     * with {@code ElementType.ALL}). Fabric's {@link HudRenderCallback} fires
     * after the vanilla HUD, the modern equivalent. Suppressed while the camera
     * editor is open (legacy {@code !CameraHandler.isCameraEditorOpen()}) so the
     * HUD and the camera-editor overlay drawable never draw simultaneously.
     *
     * <p>Centered horizontally, anchored at ¾ of the scaled screen height
     * ({@code h / 2 + h / 4}); {@code w/h} come from the window's scaled size
     * (the {@code ScaledResolution} replacement). All the {@code waveform_*}
     * gating lives inside {@code AudioRenderer.renderAll}.</p>
     */
    private void registerAudioOverlay()
    {
        HudRenderCallback.EVENT.register((context, tickDelta) ->
        {
            if (CameraHandler.isCameraEditorOpen())
            {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc == null || mc.getWindow() == null)
            {
                return;
            }

            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            int width = (int) (w * Blockbuster.audioWaveformWidth.get());

            /* Bind the frame's DrawContext so the GuiDraw shim renders through
             * the same matrix stack (headless no-ops when unbound). */
            GuiDraw.bindDrawContext(context);
            AudioRenderer.renderAll((w - width) / 2, h / 2 + h / 4, width, Blockbuster.audioWaveformHeight.get(), w, h);
        });
    }

    /**
     * Client half of the S2 networking wiring (see
     * {@code Blockbuster.registerNetworking()}): the client→server sender +
     * CLIENT-side receiver registration (P23), the P8 translation seam that
     * {@code PacketConfirm}'s IKeys render through, the handshake flag (P27),
     * the one disconnect hook owning all client network state, and the
     * client-tick drains (P28).
     */
    private void registerNetworking()
    {
        /* LangKey moved to the main source set for PacketConfirm (P26);
         * install the real client translator over the key-echo default */
        LangKey.translator = I18n::translate;

        ClientDispatcherHooks.install();
        ClientDispatcherHooks.registerClientReceivers(mchorse.mclib.network.mclib.Dispatcher.DISPATCHER);
        /* P182: Aperture camera-networking channel (registered common-side in
         * aperture CommonProxy.load — CLIENT registrations recorded there) */
        ClientDispatcherHooks.registerClientReceivers(mchorse.aperture.network.Dispatcher.DISPATCHER);
        /* P55: bundled Metamorph morph-sync channel (metamorph:*). register()
         * is idempotent — it already ran common-side in MorphHandler.register();
         * this wires the Fabric CLIENT receivers the dispatcher recorded. */
        Dispatcher.register();
        ClientDispatcherHooks.registerClientReceivers(Dispatcher.DISPATCHER);
        /* P116 control plane + P115/P117 frame/action families: the whole
         * blockbuster:* channel client-side receivers (delivery to the client
         * record mirror). register() is idempotent — it already ran server-side
         * in Blockbuster.registerNetworking(); this wires the Fabric CLIENT
         * receivers the dispatcher recorded. */
        mchorse.blockbuster.network.Dispatcher.register();
        ClientDispatcherHooks.registerClientReceivers(mchorse.blockbuster.network.Dispatcher.DISPATCHER);

        /* P27: handshake receiver (netty thread — flag only) + reset */
        ClientPlayNetworking.registerGlobalReceiver(ChannelLedger.HANDSHAKE,
            (client, handler, buf, responseSender) -> ClientNetworkState.onHandshake(buf));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientNetworkState.resetHandshake());

        /* P28: next-tick queue drain + P26 answer-timeout sweep */
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            NextTickQueue.CLIENT.drain();
            AbstractClientHandlerAnswer.sweep(System.currentTimeMillis());
        });

        /* P22.1: TickHandler client dispatch — CLIENT both phases; PLAYER +
         * the synthesized WORLD_CLIENT fire only while the local player
         * exists (the legacy WorldClientTickEvent adapter workaround).
         *
         * S22 P267 — the two tick types have *different* pause behaviour and
         * always did: legacy CLIENT came from TickEvent.ClientTickEvent, posted
         * by FMLCommonHandler.onPreClientTick/onPostClientTick at the top and
         * bottom of Minecraft.runTick, outside every isGamePaused guard; legacy
         * PLAYER and WORLD_CLIENT both came from TickEvent.PlayerTickEvent
         * (TickHandler.onPlayerTick rewrote the key to WorldClientTickEvent for
         * the local player), which the client only reached through
         * world.updateEntities() inside `if (!this.isGamePaused)`. So CLIENT
         * keeps firing while paused and the other two must not — the port's one
         * Fabric event carries both, which is why the gate is here rather than
         * on the whole listener. */
        ClientTickEvents.START_CLIENT_TICK.register(BlockbusterClient::dispatchTickHandlerStart);
        ClientTickEvents.END_CLIENT_TICK.register(BlockbusterClient::dispatchTickHandlerEnd);

        /* P22: RenderingHandler frame flag around the world-render span */
        WorldRenderEvents.START.register(
            context -> mchorse.mclib.events.RenderingHandler.setRendering(true));
        WorldRenderEvents.END.register(
            context -> mchorse.mclib.events.RenderingHandler.setRendering(false));

        /* P143: the immersive editor's per-frame morph swap + orbit-camera
         * teleport. Legacy ran these from onRenderTick Phase.START/END on the
         * GuiImmersiveMorphMenu, which was only subscribed to the event bus
         * between show() and close() — GuiImmersiveEditor.current is that
         * register/unregister analog, so the null check IS the subscription.
         *
         * The two hooks live on GameRendererMixin, NOT on WorldRenderEvents:
         * START must run before GameRenderer#renderWorld positions the camera
         * (WorldRenderEvents.START is already past camera.update, which made the
         * orbit lag a frame) and END must run after the whole frame including
         * the GUI pass, matching Forge's RenderTickEvent phases. */
    }

    /**
     * P22.1 START-phase dispatch for {@code TickHandler} — and the one place in
     * the port where the <b>two</b> legacy client tick events are visibly
     * different animals (S22 P267).
     *
     * <p>{@code TickType.CLIENT} is the port of {@code TickEvent.ClientTickEvent},
     * posted by {@code FMLCommonHandler.onPreClientTick()} as the <i>first</i>
     * statement of {@code Minecraft.runTick} — outside every
     * {@code isGamePaused} branch, so it fired behind the escape menu and must
     * keep doing so here. {@code PLAYER} and the synthesized
     * {@code WORLD_CLIENT} are the port of {@code TickEvent.PlayerTickEvent}
     * (legacy {@code TickHandler.onPlayerTick} rewrote the key to
     * {@code WorldClientTickEvent} for the local player), which the client only
     * reached through {@code world.updateEntities()} inside
     * {@code if (!this.isGamePaused)} — so those two must not.</p>
     *
     * <p>Fabric hands both to the same listener, which is why the gate is inside
     * the body rather than on the registration. Named methods (not inline
     * lambdas) so {@code ClientTickPauseGateTest} can read the split out of the
     * bytecode: the CLIENT dispatch must be emitted <i>before</i> the
     * {@code isPaused()} branch.</p>
     */
    static void dispatchTickHandlerStart(MinecraftClient client)
    {
        Blockbuster.tickHandler.runRunnables(TickHandler.TickType.CLIENT, Side.CLIENT, TickHandler.Phase.START);

        if (client.player != null && !client.isPaused())
        {
            Blockbuster.tickHandler.runRunnables(TickHandler.TickType.PLAYER, Side.CLIENT, TickHandler.Phase.START);
            Blockbuster.tickHandler.runRunnables(TickHandler.TickType.WORLD_CLIENT, Side.CLIENT, TickHandler.Phase.START);
        }
    }

    /** END-phase half of {@link #dispatchTickHandlerStart(MinecraftClient)}. */
    static void dispatchTickHandlerEnd(MinecraftClient client)
    {
        Blockbuster.tickHandler.runRunnables(TickHandler.TickType.CLIENT, Side.CLIENT, TickHandler.Phase.END);

        if (client.player != null && !client.isPaused())
        {
            Blockbuster.tickHandler.runRunnables(TickHandler.TickType.PLAYER, Side.CLIENT, TickHandler.Phase.END);
            Blockbuster.tickHandler.runRunnables(TickHandler.TickType.WORLD_CLIENT, Side.CLIENT, TickHandler.Phase.END);
        }
    }

    /**
     * P88 skin bootstrap. On init: create the six per-model {@code skins}
     * folders (+ {@code config/blockbuster/skins}) and copy the mod GUI icon to
     * {@code models/image/skins/default.png} when absent.
     *
     * <p>The 30-tick rescan that used to be inlined here is legacy
     * {@code PlayerHandler.updateClient}'s third duty and moved into
     * {@link #registerPlayerTick()} with S22 P247 — both the counter and the
     * ordering against the other four duties now live in one place.</p>
     */
    private void registerSkins()
    {
        BlockbusterPaths.createClientFolders();
        BlockbusterPaths.copyDefaultSkin();
    }

    /**
     * S22 P247 — the client player-tick loop.
     *
     * <p>Legacy pumped a {@code PlayerTickEvent} on the client every tick and
     * the port simply never wrote the handler, which left three things broken:
     * the four {@link mchorse.blockbuster.common.item.ItemGun} tick statics had
     * no caller at all (a reload parked the gun in {@code RELOADING} forever),
     * the client never ran the morph loop (no squid-air drain, frozen
     * {@code IMorphing.getAnimation()} transition fade), and the five
     * {@code updateClient} duties were four independent {@code END_CLIENT_TICK}
     * registrations whose relative order was accidental.</p>
     *
     * <p>This method installs the five client duty seams on
     * {@link mchorse.blockbuster.events.PlayerHandler} (which lives in
     * {@code src/main} because legacy registered the class from
     * {@code CommonProxy} — see its class doc) and drives its two phases from
     * {@code START_CLIENT_TICK}/{@code END_CLIENT_TICK} with {@code mc.player},
     * which is legacy's {@code Minecraft.getMinecraft().player == event.player}
     * gate.</p>
     *
     * <p>No double-fire on an integrated server: the server pump iterates
     * {@code ServerPlayerEntity}s while this one only ever sees the
     * {@code ClientPlayerEntity}, exactly as Forge's two-sided
     * {@code PlayerTickEvent} did.</p>
     *
     * <p><b>Both hooks are pause-gated (S22 P267)</b> — see
     * {@code PlayerHandler}'s "Pause parity" note. Forge's client
     * {@code PlayerTickEvent} could not fire behind the escape menu; Fabric's
     * client ticks do, so the pause state is passed in explicitly. The lint that
     * keeps the next subscriber honest is
     * {@code ClientTickPauseGateTest}.</p>
     */
    private void registerPlayerTick()
    {
        PlayerHandler.modelCachePump =
            TileEntityModelItemStackRenderer::tickCache;
        PlayerHandler.gunCachePump =
            TileEntityGunItemStackRenderer::updateClient;
        PlayerHandler.skinsRescan =
            SkinHandler::checkSkinsFolder;
        PlayerHandler.emitterPump =
            RenderingHandler::updateEmitters;
        PlayerHandler.gifPump =
            GifTexture::updateTick;

        ClientTickEvents.START_CLIENT_TICK.register(client ->
            PlayerHandler.INSTANCE.startTick(client.player, client.isPaused()));

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            PlayerHandler.INSTANCE.endTickClient(client.player, client.isPaused());

            /* P290: legacy's client PlayerTickEvent also fired for every
             * EntityOtherPlayerMP (WorldClient.updateEntities → onUpdate), so
             * the morph loop runs for the rest of the world's players too. */
            PlayerHandler.INSTANCE.endTickClientOtherPlayers(client.player,
                client.world == null ? null : client.world.getPlayers(), client.isPaused());
        });
    }

    /**
     * P88.1: build and register the two file trees the texture picker (and every
     * {@code getPresets} skin list) browses. Legacy {@code ClientProxy.load}:
     *
     * <pre>
     * BlockbusterJarTree jarTree = new BlockbusterJarTree();
     *
     * GlobalTree.TREE.register(tree = new BlockbusterTree(this.pack.folders.get(0)));
     * GlobalTree.TREE.register(jarTree);
     *
     * FileTree.addBackEntry(jarTree.root);
     * </pre>
     *
     * <p>Two port adjustments:</p>
     * <ul>
     *   <li>{@code pack.folders.get(0)} is the config models folder wrapped in an
     *       {@link mchorse.blockbuster.utils.mclib.ImageFolder} — legacy did that
     *       wrapping inside {@code ModelPack.setupFolders}, where it also leaked
     *       into the model scan. The port wraps here instead (the SEAM(S3) note
     *       on {@code ModelPack}), which is equivalent for the tree: the
     *       {@code ImageFolder} re-wraps sub-directories on every
     *       {@code listFiles()}, so GIF pseudo-folders appear at every depth.
     *       Registration therefore does not depend on {@code CommonProxy.pack}
     *       having been built yet (it is created lazily on world join).</li>
     *   <li>The jar tree is registered empty and populated from the client
     *       resource-reload listener: at client-init time the initial resource
     *       reload has not run, and in 1.20.4 the pack stack can change at
     *       runtime. {@link mchorse.blockbuster.utils.mclib.BlockbusterJarTree#populate}
     *       rebuilds in place, keeping the single registered branch.</li>
     * </ul>
     */
    private void registerFileTrees()
    {
        ClientProxy.jarTree = new BlockbusterJarTree();
        ClientProxy.tree = new BlockbusterTree(
            new ImageFolder(
                BlockbusterPaths.models().toFile().getPath()));

        GlobalTree.TREE.register(ClientProxy.tree);
        GlobalTree.TREE.register(ClientProxy.jarTree);

        FileTree.addBackEntry(ClientProxy.jarTree.root);

        /* The client half of BlockbusterSection.getSkin — a remote model with no
         * defaultTexture picks its picker icon out of this same tree. */
        BlockbusterSectionSkins.install();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new SimpleSynchronousResourceReloadListener()
            {
                @Override
                public Identifier getFabricId()
                {
                    return new Identifier(Blockbuster.MOD_ID, "jar_tree");
                }

                @Override
                public void reload(ResourceManager manager)
                {
                    ClientProxy.jarTree.populate(
                        ResourcePackUtils.getAllPictures(manager));
                }
            });
    }

    /**
     * P124: register the recording overlay HUD and its disconnect cleanup.
     *
     * <p>{@code HudRenderCallback} fires late (after the vanilla HUD, matching
     * 1.12.2's {@code RenderGameOverlayEvent.Post(ALL)}); the overlay draws
     * regardless of the debug HUD, as legacy did.</p>
     *
     * <p>Disconnect cleanup ports legacy {@code KeyboardHandler.onUserLogOut}:
     * flush the client record-manager mirror ({@code manager.reset()}) and hide
     * the overlay so a stale REC indicator / record cache never survives into
     * the next server session. (The {@code resetEmitters()} half of that legacy
     * handler belongs to the particles stage, S13.)</p>
     */
    private void registerRecordingOverlay()
    {
        HudRenderCallback.EVENT.register((context, tickDelta) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.getWindow() != null)
            {
                recordingOverlay.draw(context, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            ClientProxy.manager.reset();
            ClientProxy.recordingOverlay.setVisible(false);
        });
    }
}
