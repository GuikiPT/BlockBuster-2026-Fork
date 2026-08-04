package mchorse.metamorph.api;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;

/**
 * Morph factory (roadmap P49).
 *
 * <p>Provides morph resources (models, abilities, attacks, actions).
 * {@link MorphManager} iterates factories in <b>reverse registration order</b>
 * so later-registered mods win.</p>
 *
 * <p>Port note: the legacy client-only {@code registerMorphEditors(Minecraft,
 * List&lt;GuiAbstractMorph&gt;)} method is omitted here — this data-core
 * interface (main source set) cannot reference client-only GUI classes, and the
 * dispatch needs only these three methods. The editor half is ported as the
 * client-source {@code mchorse.metamorph.client.gui.editor.IMorphEditorFactory}
 * collected by {@code MorphEditorRegistry} (P165-registry).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/IMorphFactory.java
 */
public interface IMorphFactory
{
    /**
     * Register here everything that is required by morph manager system
     */
    public void register(MorphManager manager);

    /**
     * Does this factory have a morph by given name?
     */
    public boolean hasMorph(String name);

    /**
     * Get a morph from NBT
     */
    public AbstractMorph getMorphFromNBT(NbtCompound tag);
}
