package mchorse.metamorph.api.creative.categories;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Recent-morphs category (roadmap P57).
 *
 * <p>Faithful port: new morphs are inserted at index 0 (newest first); while
 * the size is {@code >=} the {@code max_recent_morphs} cap (config default 20,
 * client-side, range 1–200) the tail is evicted. Only morphs currently in the
 * list are editable.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/categories/RecentCategory.java
 */
public class RecentCategory extends MorphCategory
{
    public RecentCategory(MorphSection parent, String title)
    {
        super(parent, title);
    }

    @Override
    protected void addMorph(AbstractMorph morph)
    {
        while (this.morphs.size() >= Metamorph.maxRecentMorphs.get())
        {
            this.morphs.remove(this.morphs.size() - 1);
        }

        this.morphs.add(0, morph);
    }

    @Override
    public boolean isEditable(AbstractMorph morph)
    {
        return this.morphs.indexOf(morph) != -1;
    }
}
