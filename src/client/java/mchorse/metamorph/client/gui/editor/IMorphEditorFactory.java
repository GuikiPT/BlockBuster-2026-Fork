package mchorse.metamorph.client.gui.editor;

import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Client-side morph-editor factory (roadmap P165-registry).
 *
 * <p>This is the Fabric split-source counterpart of the legacy
 * {@code IMorphFactory.registerMorphEditors(Minecraft, List&lt;GuiAbstractMorph&gt;)}
 * client method. In Metamorph 1.4 the editor-registration hook was a
 * {@code @SideOnly(Side.CLIENT)} method declared directly on
 * {@code IMorphFactory}; here the data-core {@link mchorse.metamorph.api.IMorphFactory}
 * lives in the <b>main</b> source set and cannot reference the client-only
 * {@link GuiAbstractMorph}, so the editor half is broken out into this
 * client-source interface and collected by {@link MorphEditorRegistry}.</p>
 *
 * <p>A factory adds one {@link GuiAbstractMorph} per morph type it owns into the
 * shared {@code editors} list. Which concrete editor is later chosen for a morph
 * is decided by the first {@link GuiAbstractMorph#canEdit} that returns true, so
 * the add-order within a factory (and the factory registration order — see
 * {@link MorphEditorRegistry#getMorphEditors}) is load-bearing: more specific
 * editors must be added before the fall-through {@code GuiCustomMorph}.</p>
 *
 * Legacy source: {@code IMorphFactory.registerMorphEditors} in
 * .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/IMorphFactory.java
 */
@FunctionalInterface
public interface IMorphEditorFactory
{
    /**
     * Add every morph editor this factory owns to {@code editors}, in
     * most-specific-first order.
     */
    void registerMorphEditors(MinecraftClient mc, List<GuiAbstractMorph> editors);
}
