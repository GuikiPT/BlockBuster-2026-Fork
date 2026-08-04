package mchorse.blockbuster_pack.trackers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bidirectional tracker registry (port of Blockbuster 2.7.2's
 * {@code TrackerRegistry}, roadmap P166).
 *
 * <p>Maps a stable string id ↔ {@link BaseTracker} subclass. Both maps are
 * {@link LinkedHashMap}s: <b>insertion order is load-bearing</b> — it defines
 * the order the editor's type button cycles through, and the ids
 * ({@code "aperture_tracker"}, {@code "apcam"}) are serialised into the morph
 * NBT (a wire/disk contract). See {@link #registerDefaults()}.</p>
 *
 * <p>Port note: legacy declared the client GUI map ({@code CLIENT}) with a
 * {@code @SideOnly(CLIENT)} {@code GuiBaseTracker} value type. On Fabric the
 * client GUI classes are unreachable from the {@code main} source set, so the
 * value type is loosened to {@link Object}; client code populates it with (and
 * casts it back to) {@code GuiBaseTracker} instances — see
 * {@code GuiTrackerMorph.registerClientTrackers}.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/trackers/TrackerRegistry.java
 */
public class TrackerRegistry
{
    public static final Map<String, Class<? extends BaseTracker>> ID_TO_CLASS = new LinkedHashMap<String, Class<? extends BaseTracker>>();

    public static final Map<Class<? extends BaseTracker>, String> CLASS_TO_ID = new LinkedHashMap<Class<? extends BaseTracker>, String>();

    /**
     * Client-only editor panel map ({@code Class<? extends BaseTracker> ->
     * GuiBaseTracker}). Loosely typed because the GUI classes live in the
     * client source set; populated on client init.
     */
    public static Map<Class<? extends BaseTracker>, Object> CLIENT;

    public static void registerTracker(String id, Class<? extends BaseTracker> clazz)
    {
        ID_TO_CLASS.put(id, clazz);
        CLASS_TO_ID.put(clazz, id);
    }

    /**
     * Register the built-in trackers in the legacy order (matches
     * {@code CommonProxy} lines 182–183 of Blockbuster 2.7.2). Idempotent — a
     * {@link LinkedHashMap} re-put of the same mapping preserves order, so it
     * is safe for the morph-pack scaffolding (P157) to call this too.
     *
     * <p>batch-4 integration: P157's {@code CommonProxy} owns the canonical
     * registration call. This helper keeps P166 self-contained (deterministic
     * NBT ids + GUI cycle order for the headless tests) and may be folded into
     * that call site at integration.</p>
     */
    public static void registerDefaults()
    {
        registerTracker("aperture_tracker", MorphTracker.class);
        registerTracker("apcam", ApertureCamera.class);
    }
}
