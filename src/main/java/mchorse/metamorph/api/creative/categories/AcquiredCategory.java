package mchorse.metamorph.api.creative.categories;

import mchorse.metamorph.api.creative.CreativeMorphNetwork;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Acquired-morphs category (roadmap P57).
 *
 * <p>Faithful port: this category proxies every mutation to the server via
 * packets ({@code acquire} / {@code sync} / {@code remove} / {@code select} /
 * {@code clear}) and is rebuilt from the morphing component each picker update
 * through {@link #setMorphs(List)}. The packet sends are routed through the
 * {@link CreativeMorphNetwork} seam (SEAM P54/P55) rather than referencing the
 * packet classes directly.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/categories/AcquiredCategory.java
 */
public class AcquiredCategory extends MorphCategory
{
    public AcquiredCategory(MorphSection parent, String title)
    {
        super(parent, title);
    }

    public void setMorphs(List<AbstractMorph> morphs)
    {
        this.morphs = morphs;
    }

    @Override
    public void clear()
    {
        super.clear();

        CreativeMorphNetwork.INSTANCE.clearAcquired();
    }

    @Override
    protected void addMorph(AbstractMorph morph)
    {
        super.addMorph(morph);

        CreativeMorphNetwork.INSTANCE.acquireMorph(morph);
    }

    @Override
    public boolean isEditable(AbstractMorph morph)
    {
        return this.morphs.indexOf(morph) != -1;
    }

    @Override
    public void edit(AbstractMorph morph)
    {
        int index = this.morphs.indexOf(morph);

        if (index >= 0)
        {
            CreativeMorphNetwork.INSTANCE.syncMorph(morph, index);
        }
    }

    @Override
    public boolean remove(AbstractMorph morph)
    {
        int index = this.morphs.indexOf(morph);
        boolean has = index != -1;

        if (has)
        {
            CreativeMorphNetwork.INSTANCE.removeMorph(index);
        }

        return has;
    }

    @Override
    protected boolean morph(PlayerEntity player, AbstractMorph morph)
    {
        int index = this.morphs.indexOf(morph);

        if (index >= 0)
        {
            CreativeMorphNetwork.INSTANCE.selectMorph(index);
        }

        return true;
    }
}
