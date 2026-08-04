package mchorse.vanilla_pack.editors;

import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.IMorphEditorFactory;
import mchorse.metamorph.client.gui.editor.MorphEditorRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Metamorph's own client-side morph-editor factory (roadmap P59.2).
 *
 * <p>Port of the client half of legacy
 * {@code mchorse.vanilla_pack.MetamorphFactory.registerMorphEditors}. As with
 * Blockbuster's {@code BlockbusterMorphEditors}, the Fabric split cannot hang a
 * client-only editor list off the main-source
 * {@link mchorse.vanilla_pack.MetamorphFactory}, so the editor half lives here
 * and registers into the client {@link MorphEditorRegistry}.</p>
 *
 * <p><b>The add-order is behaviour.</b> Editor lookup takes the first
 * {@code canEdit} match, so:</p>
 *
 * <ul>
 *   <li>{@link GuiPlayerMorph} precedes {@link GuiEntityMorph} — a player
 *       disguise <i>is</i> an {@code EntityMorph} (it is one literally now:
 *       {@code GuiPlayerMorph} extends {@code GuiEntityMorph} and only narrows
 *       {@code canEdit}), so the entity editor would otherwise claim it first
 *       and the username field would be unreachable;</li>
 *   <li>the trailing bare {@link GuiAbstractMorph} is the catch-all: its
 *       {@code canEdit} accepts any non-null morph, so it must stay last, and
 *       this factory must be the <b>first</b> registered so
 *       {@link MorphEditorRegistry}'s reverse-order walk puts it last overall
 *       (legacy order: Metamorph's factory registered before Blockbuster's).
 *       Without it, a morph no specific editor claims — anything from a third
 *       party, or a Blockbuster morph whose editor declined — would open no
 *       editor at all rather than the settings-only one.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/MetamorphFactory.java
 * ({@code registerMorphEditors}).
 */
public class MetamorphMorphEditors implements IMorphEditorFactory
{
    /** Shared instance registered at client init (registry dedupes by identity). */
    public static final MetamorphMorphEditors INSTANCE = new MetamorphMorphEditors();

    /**
     * Register Metamorph's editor factory into the shared client registry.
     * Idempotent — safe to call on every client init / world reload.
     */
    public static void register()
    {
        MorphEditorRegistry.INSTANCE.register(INSTANCE);
    }

    @Override
    public void registerMorphEditors(MinecraftClient mc, List<GuiAbstractMorph> editors)
    {
        editors.add(new GuiLabelMorph(mc));
        editors.add(new GuiItemMorph(mc));
        editors.add(new GuiBlockMorph(mc));
        editors.add(new GuiPlayerMorph(mc));
        editors.add(new GuiEntityMorph(mc));
        editors.add(new GuiAbstractMorph(mc));
    }
}
