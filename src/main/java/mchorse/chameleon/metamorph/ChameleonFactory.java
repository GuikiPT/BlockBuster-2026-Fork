package mchorse.chameleon.metamorph;

import mchorse.metamorph.api.IMorphFactory;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;

/**
 * Chameleon morph factory
 *
 * This factory is responsible for adding all custom modeled morphs provided by
 * a user (in his config folder)
 *
 * <p>Port note: the legacy client-only {@code registerMorphEditors(Minecraft,
 * List&lt;GuiAbstractMorph&gt;)} is intentionally absent from this main-source
 * {@link IMorphFactory} (see that interface's javadoc). Its port is the
 * client-source {@code mchorse.chameleon.client.gui.ChameleonMorphEditors},
 * registered into {@code MorphEditorRegistry} — same treatment as
 * {@code BlockbusterMorphEditors} (P165-registry).</p>
 *
 * Legacy source: chameleon/.../metamorph/ChameleonFactory.java
 */
public class ChameleonFactory implements IMorphFactory
{
    public ChameleonSection section;

    @Override
    public void register(MorphManager manager)
    {
        manager.list.register(this.section = new ChameleonSection("chameleon"));
    }

    @Override
    public AbstractMorph getMorphFromNBT(NbtCompound tag)
    {
        ChameleonMorph morph = new ChameleonMorph();

        morph.fromNBT(tag);

        return morph;
    }

    /**
     * Claims every {@code chameleon.}-prefixed name, and nothing else.
     *
     * <p>Unlike {@code BlockbusterFactory} there is no bare-id branch: a
     * Chameleon morph is <b>always</b> named after its model folder with the
     * prefix, so the check is the whole contract.</p>
     */
    @Override
    public boolean hasMorph(String morph)
    {
        return morph.startsWith("chameleon.");
    }
}
