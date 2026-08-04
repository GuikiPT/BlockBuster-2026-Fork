package mchorse.chameleon.client.gui;

import mchorse.chameleon.metamorph.editor.GuiChameleonMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.IMorphEditorFactory;
import mchorse.metamorph.client.gui.editor.MorphEditorRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Chameleon's client-side morph-editor factory.
 *
 * <p>Port of the client half of legacy
 * {@code ChameleonFactory.registerMorphEditors}. In the Fabric split the
 * client-only editor cannot hang off the main-source
 * {@link mchorse.chameleon.metamorph.ChameleonFactory} (which implements the
 * data-core {@code IMorphFactory}); it lives here and registers into the client
 * {@link MorphEditorRegistry} — the same treatment
 * {@code BlockbusterMorphEditors} got (P165-registry).</p>
 */
public class ChameleonMorphEditors implements IMorphEditorFactory
{
    /** Shared instance registered at client init (registry dedupes by identity). */
    public static final ChameleonMorphEditors INSTANCE = new ChameleonMorphEditors();

    /**
     * Register Chameleon's editor factory into the shared client registry.
     * Idempotent — safe to call on every client init / world reload.
     */
    public static void register()
    {
        MorphEditorRegistry.INSTANCE.register(INSTANCE);
    }

    @Override
    public void registerMorphEditors(MinecraftClient mc, List<GuiAbstractMorph> editors)
    {
        editors.add(new GuiChameleonMorph(mc));
    }
}
