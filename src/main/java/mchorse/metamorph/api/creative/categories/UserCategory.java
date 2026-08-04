package mchorse.metamorph.api.creative.categories;

import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * User-defined global category (roadmap P57).
 *
 * <p>Faithful port: its {@link #getTitle()} returns the <b>raw</b> title (not a
 * localized key) — this is the string persisted to {@code list.json}. Every
 * add / edit / remove triggers a {@link UserSection#save()} when the parent is
 * a {@link UserSection}. Only morphs currently in the list are editable and
 * {@link #isHidden()} honors the {@code hidden} flag even when empty.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/categories/UserCategory.java
 */
public class UserCategory extends MorphCategory
{
    public UserCategory(MorphSection parent, String title)
    {
        super(parent, title);
    }

    @Override
    public String getTitle()
    {
        return this.title;
    }

    @Override
    public boolean isHidden()
    {
        return this.hidden;
    }

    @Override
    protected void addMorph(AbstractMorph morph)
    {
        super.addMorph(morph);

        if (this.parent instanceof UserSection)
        {
            ((UserSection) this.parent).save();
        }
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

        if (index >= 0 && this.parent instanceof UserSection)
        {
            ((UserSection) this.parent).save();
        }
    }

    @Override
    public boolean remove(AbstractMorph morph)
    {
        boolean result = super.remove(morph);

        if (result && this.parent instanceof UserSection)
        {
            ((UserSection) this.parent).save();
        }

        return result;
    }
}
