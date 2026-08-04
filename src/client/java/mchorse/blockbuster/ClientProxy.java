package mchorse.blockbuster;

import java.util.function.BooleanSupplier;
import mchorse.blockbuster.api.StructureReloader;
import mchorse.blockbuster.audio.AudioLibrary;
import mchorse.blockbuster.client.gui.RecordingOverlayState;
import mchorse.blockbuster.client.particles.BedrockLibrary;
import mchorse.blockbuster.recording.RecordManager;
import mchorse.blockbuster.utils.mclib.BlockbusterJarTree;
import mchorse.blockbuster.utils.mclib.BlockbusterTree;
import mchorse.mclib.utils.files.entries.FolderEntry;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side holder mirroring legacy {@code ClientProxy} statics (roadmap
 * P116). Legacy kept the client-side {@code manager} (a {@link RecordManager}
 * mirror that records the local player and caches records received from the
 * server) and the {@code recordingOverlay} the caption/recording packets drive.
 *
 * <p>The dashboard panels holder lives on {@link BlockbusterClient#panels}
 * (P135); {@code ClientProxy.panels} call sites read it from there.</p>
 */
public class ClientProxy
{
    /**
     * Client record manager mirror. Distinct from {@code CommonProxy.manager}
     * (server): it records the local player during frame capture and caches
     * records delivered by the frame packets / loaded from jar assets via
     * {@link RecordManager#getClient(String)}.
     */
    public static RecordManager manager = new RecordManager();

    /**
     * Recording overlay state (visible / caption / filename-mode), rendered by
     * the S10 P124 HUD. Driven by {@code PacketCaption} and
     * {@code PacketPlayerRecording}.
     */
    public static RecordingOverlayState recordingOverlay = new RecordingOverlayState();

    /**
     * Client-side {@code .wav} library (P188). Legacy {@code ClientProxy.audio}
     * ({@code new AudioLibrary(new File(CommonProxy.configFile, "audio"))}).
     *
     * <p>SEAM(P188.1/P189): this holder is instantiated + reset by the client
     * audio lifecycle wiring (init, resource-reload, dashboard-close). Until
     * that lands it stays {@code null}; {@link mchorse.blockbuster.audio.AudioRenderer#renderAll}
     * null-guards so the P190 HUD hook never NPEs (legacy always had it set).</p>
     */
    public static AudioLibrary audio;

    /**
     * Client-side Bedrock particle-scheme library (P141). Legacy
     * {@code Blockbuster.proxy.particles} — the on-disk catalogue the Snowstorm
     * editor ({@link mchorse.blockbuster.client.gui.dashboard.panels.snowstorm.GuiSnowstorm})
     * saves/loads through.
     *
     * <p>Built once at client init by
     * {@link mchorse.blockbuster.client.particles.ParticleLibraryWiring#install()}
     * (legacy {@code CommonProxy} line 124), pointing at
     * {@code config/blockbuster/models/particles} via {@code BlockbusterPaths}.
     * {@link mchorse.blockbuster.client.particles.BedrockLibrary#reload()} runs
     * from the two legacy triggers and no others: world join (after the model
     * reload) and the creative-picker refresh. Legacy had no file watcher here.</p>
     *
     * <p><b>Exactly one writer</b>, deliberately (S22 batch U-K). Until that
     * batch this field had <i>no</i> production writer at all — only a lazy
     * branch in the Snowstorm dashboard panel — so every Snowstorm morph
     * resolved a {@code null} scheme and rendered nothing unless the user
     * happened to open that panel. The panel is now a pure reader: the instance
     * is identity-significant ({@code SnowstormClient.reloadIfStale} compares a
     * live emitter's scheme against the library's), so a second writer would
     * force every emitter in the world to rebuild.</p>
     *
     * <p>Stays {@code null} on a dedicated server and in headless tests; every
     * reader null-guards.</p>
     */
    public static BedrockLibrary particles;

    /**
     * The custom-model skin file tree (P88.1). Legacy
     * {@code ClientProxy.tree = new BlockbusterTree(this.pack.folders.get(0))},
     * registered into {@link mchorse.mclib.utils.files.GlobalTree#TREE} next to
     * {@link mchorse.blockbuster.utils.mclib.BlockbusterJarTree}. Rooted
     * {@code "b.a"}, so its paths start at a model folder name
     * ({@code "steve/skins"}) — a {@code GlobalTree.TREE} lookup for the same
     * folder needs the {@code "b.a/"} prefix.
     *
     * <p>Null until {@code BlockbusterClient} builds it (and in headless tests);
     * call sites go through {@link #skins(String)} rather than dereferencing it.</p>
     */
    public static BlockbusterTree tree;

    /**
     * The jar-side picture tree (P88.1) — kept so the client resource-reload
     * listener can rebuild it in place.
     */
    public static BlockbusterJarTree jarTree;

    /**
     * Whether a client player is currently in a world — the guard legacy's
     * resource-reload lambda put on its {@code StructureMorph.reloadStructures()}
     * half ({@code Minecraft.getMinecraft().player != null}).
     *
     * <p>Held as a replaceable supplier so {@link #onResourceReload()} is
     * headlessly testable without loading {@link MinecraftClient} (whose static
     * init is not safe outside a real client).</p>
     */
    public static BooleanSupplier clientPlayerPresent = ClientProxy::hasClientPlayer;

    private static boolean hasClientPlayer()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc != null && mc.player != null;
    }

    /**
     * P188.1 — the body of legacy {@code ClientProxy.load}'s resource-reload
     * listener (1.12.2 lines 256–263):
     *
     * <pre>((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener((manager) -&gt; {
     *     audio.reset();
     *     if (Minecraft.getMinecraft().player != null) { StructureMorph.reloadStructures(); }
     * });</pre>
     *
     * <p>Both halves live in <b>one</b> listener exactly as in 1.12.2. The
     * {@code audio.reset()} half is S16/P188.1's: a resource reload (F3+T, pack
     * change) drops every loaded {@code .wav}, deleting its AL buffer/source and
     * its waveform texture, so the next audio packet reloads from disk. It runs
     * <b>unconditionally</b> — no player guard. The
     * {@code StructureMorph.reloadStructures()} half is S14's; it keeps its
     * {@code player != null} guard (it re-requests structures from the server)
     * and goes through the {@link mchorse.blockbuster.api.StructureReloader}
     * indirection, which is a no-op until the structure morph installs its
     * handler.</p>
     *
     * <p>The {@code audio} null-guard is a port addition for headless/pre-init
     * paths; legacy always had the field set by then.</p>
     */
    public static void onResourceReload()
    {
        /* S16 P188.1 half — unconditional */
        if (audio != null)
        {
            audio.reset();
        }

        /* S14 half (single shared listener, legacy shape) */
        if (clientPlayerPresent.getAsBoolean())
        {
            StructureReloader.reload();
        }
    }

    /**
     * Null-safe {@code tree.getByPath(path, null)}: every legacy call site
     * dereferenced {@code ClientProxy.tree} directly, which is only ever set on
     * a real client. Headless tests (and any call before client init) get
     * {@code null}, which every caller already treats as "no skins folder".
     */
    public static FolderEntry skins(String path)
    {
        return tree == null ? null : tree.getByPath(path, null);
    }
}
