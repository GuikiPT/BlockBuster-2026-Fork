package mchorse.blockbuster_pack.client.gui;

import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.IMorphEditorFactory;
import mchorse.metamorph.client.gui.editor.MorphEditorRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Blockbuster's client-side morph-editor factory (roadmap P165-registry).
 *
 * <p>Faithful port of the client half of legacy
 * {@code mchorse.blockbuster_pack.BlockbusterFactory.registerMorphEditors} — the
 * method that supplied every {@code Gui*Morph} editor to Metamorph's morph
 * editor / creative picker. In the Fabric split the client-only editor list
 * cannot hang off the main-source {@link mchorse.blockbuster_pack.BlockbusterFactory}
 * (which implements the data-core {@code IMorphFactory}); it lives here instead
 * and registers into the client {@link MorphEditorRegistry}.</p>
 *
 * <p>The add-order is preserved verbatim from legacy so the {@code canEdit}
 * selection resolves identically: every specific editor precedes the
 * fall-through {@link GuiCustomMorph} (whose {@code canEdit} accepts any
 * {@code CustomMorph} with a resolved model).</p>
 *
 * Legacy source: blockbuster-1.12/.../mchorse/blockbuster_pack/BlockbusterFactory.java
 * ({@code registerMorphEditors}).
 */
public class BlockbusterMorphEditors implements IMorphEditorFactory
{
    /** Shared instance registered at client init (registry dedupes by identity). */
    public static final BlockbusterMorphEditors INSTANCE = new BlockbusterMorphEditors();

    /**
     * Register Blockbuster's editor factory into the shared client registry.
     * Idempotent — safe to call on every client init / world reload.
     */
    public static void register()
    {
        MorphEditorRegistry.INSTANCE.register(INSTANCE);
    }

    @Override
    public void registerMorphEditors(MinecraftClient mc, List<GuiAbstractMorph> editors)
    {
        /* ---------------------------------------------------------------------
         * EXTENSION POINT (P165-registry): sibling per-morph editor tasks add
         * their `editors.add(new GuiXxxMorph(mc));` line here, in the legacy
         * order (specific editors before the GuiCustomMorph fall-through). Keep
         * this list byte-for-byte aligned with legacy BlockbusterFactory
         * .registerMorphEditors.
         * ------------------------------------------------------------------- */
        editors.add(new GuiCustomMorph(mc));
        editors.add(new GuiImageMorph(mc));
        editors.add(new GuiSequencerMorph(mc));
        editors.add(new GuiRecordMorph(mc));
        editors.add(new GuiStructureMorph(mc));
        editors.add(new GuiParticleMorph(mc));
        editors.add(new GuiSnowstormMorph(mc));

        /* S22/P248 — the tracker editor's per-type sub-panel map.
         *
         * Legacy built it in ClientProxy.load (lines 266-269) right next to the
         * editor registration; the port shipped the populator
         * (GuiTrackerMorph.registerClientTrackers) with ZERO callers, so
         * TrackerRegistry.CLIENT stayed null and GuiTrackerMorphPanel
         * .updateTracker NPE'd on *every* tracker-morph edit (user-reported hard
         * crash, P166 hole).
         *
         * Built HERE and not in BlockbusterClient.onInitializeClient: Fabric's
         * ClientModInitializer entrypoint runs mid-MinecraftClient.<init>, before
         * `textRenderer` is assigned, and GuiElement captures the font *once*
         * (GuiElement:122) into GuiTextField's final `fontRenderer` — panels built
         * at client init would be permanently fontless instead of crashing, which
         * is worse. This registrar is invoked lazily from
         * GuiCreativeMorphsList.getMorphEditor with a fully constructed client, so
         * the panels always share the live `mc` of the GuiTrackerMorph they serve. */
        GuiTrackerMorph.registerClientTrackers(mc);
        editors.add(new GuiTrackerMorph(mc));
    }
}
