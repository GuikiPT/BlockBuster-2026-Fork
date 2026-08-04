package mchorse.metamorph.client.gui.editor;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side registry of {@link IMorphEditorFactory}s (roadmap P165-registry).
 *
 * <p>This is the editor-discovery path the legacy morph editor / creative picker
 * used through {@code MorphManager.registerMorphEditors(mc, editors)}: it walks
 * every registered factory and lets each contribute its {@link GuiAbstractMorph}
 * editors into one aggregated list, from which the selector picks the first
 * whose {@link GuiAbstractMorph#canEdit} matches the edited morph.</p>
 *
 * <p>In the Fabric split, {@code MorphManager} (main source set) cannot hold the
 * client-only editor list, so this client-source registry is the parallel home
 * of the {@code registerMorphEditors} half. It preserves the load-bearing legacy
 * ordering: factories are iterated in <b>reverse registration order</b> (see
 * {@code MorphManager.registerMorphEditors}: {@code for (i = size-1; i >= 0;
 * i--)}), so a later-registered mod's editors are offered before earlier ones.</p>
 *
 * <p>SEAM(P58): the future {@code GuiCreativeMorphsList} is the consumer — it
 * calls {@link #getMorphEditors(MinecraftClient)} once, caches the list, and
 * runs the {@code canEdit}/{@code setMorphs}/{@code startEdit} selection (which
 * needs the host and is therefore left to the consumer, exactly as legacy
 * {@code GuiCreativeMorphsList.getMorphEditor} did).</p>
 */
public class MorphEditorRegistry
{
    /** Shared production registry (client-init populates it). */
    public static final MorphEditorRegistry INSTANCE = new MorphEditorRegistry();

    /**
     * Registered editor factories, in registration order. Iterated in reverse
     * when building the editor list — see {@link #getMorphEditors}.
     */
    public final List<IMorphEditorFactory> factories = new ArrayList<IMorphEditorFactory>();

    /**
     * Register a morph-editor factory. Idempotent by identity: registering the
     * same factory instance twice is a no-op (client init can run more than once
     * across world reloads).
     */
    public void register(IMorphEditorFactory factory)
    {
        if (factory != null && !this.factories.contains(factory))
        {
            this.factories.add(factory);
        }
    }

    /**
     * Build the aggregated morph-editor list by asking every factory, in reverse
     * registration order (legacy {@code MorphManager.registerMorphEditors}
     * parity), to contribute its editors. Returns a fresh list of fresh editor
     * GUIs on every call — mirroring legacy, which built the list once per
     * {@code GuiCreativeMorphsList} instance.
     */
    public List<GuiAbstractMorph> getMorphEditors(MinecraftClient mc)
    {
        List<GuiAbstractMorph> editors = new ArrayList<GuiAbstractMorph>();

        for (int i = this.factories.size() - 1; i >= 0; i--)
        {
            this.factories.get(i).registerMorphEditors(mc, editors);
        }

        return editors;
    }
}
