package mchorse.metamorph.client.gui.creative;

import mchorse.metamorph.api.MetamorphEvents;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.MorphList;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.events.ReloadMorphs;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.GuiMorphs;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * The scrolling multi-section morph list of the creative picker (roadmap P58).
 *
 * <p>1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.creative.GuiCreativeMorphs} — the
 * subclass half of legacy's split: section setup from {@link MorphManager}'s
 * {@link MorphList}, the {@code setSelected} search, {@code syncSelected},
 * {@code copyToRecent} and the editability probe. The generic half (the
 * {@link GuiMorphSection} list, arrow-key grid navigation, the search filter,
 * {@code scrollTo} and the section-height {@code resize} pass) lives in
 * {@link GuiMorphs}, exactly as it does in legacy.</p>
 *
 * <p>Every legacy field name is preserved for parity ({@code filter} the cached
 * lower-cased search string, {@code sections}, {@code selected},
 * {@link #user}, {@link #userSection}).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiCreativeMorphs.java
 */
public class GuiCreativeMorphs extends GuiMorphs
{
    public UserSection user;
    public GuiMorphSection userSection;

    private final GuiCreativeMorphsList parent;

    public GuiCreativeMorphs(MinecraftClient mc, GuiCreativeMorphsList parent)
    {
        super(mc);

        this.parent = parent;
    }

    /* Section setup */

    public void setupSections(GuiCreativeMorphsList menu, Consumer<GuiMorphSection> callback)
    {
        MorphList list = MorphManager.INSTANCE.list;

        list.update(this.mc == null ? null : this.mc.world);
        MetamorphEvents.RELOAD_MORPHS.invoker().accept(new ReloadMorphs());

        this.sections.clear();
        this.removeAll();

        this.user = null;
        this.userSection = null;

        for (MorphSection section : list.sections)
        {
            /* Legacy dispatched to section.getGUI(...), which UserSection overrode
             * to build a GuiUserSection (right-click paste / category management).
             * MorphSection lives in the common source set here and cannot reference
             * client GUI types, so the dispatch is done here instead — same result. */
            GuiMorphSection element = section instanceof UserSection
                ? new GuiUserSection(this.mc, menu, section, callback)
                : new GuiMorphSection(this.mc, menu, section, callback);

            element.filter = this.sharedFilter;

            if (section instanceof UserSection)
            {
                this.user = (UserSection) section;
                this.userSection = element;
            }

            element.flex();
            this.sections.add(element);
            this.add(element);
        }

        if (!this.sections.isEmpty())
        {
            this.sections.get(this.sections.size() - 1).last = true;
        }

        this.navigator = new MorphGridNavigator(list.sections, this.sharedFilter);
    }

    /* Morph selection */

    @Override
    public void setSelected(AbstractMorph morph)
    {
        super.setSelected(morph);

        if (this.selected != null)
        {
            this.selected.reset();
        }

        if (morph != null)
        {
            AbstractMorph found = null;
            MorphCategory selectedCategory = null;
            GuiMorphSection selectedSection = null;

            searchForMorph:
            for (GuiMorphSection section : this.sections)
            {
                for (MorphCategory category : section.section.categories)
                {
                    found = category.getEqual(morph);

                    if (found != null)
                    {
                        selectedCategory = category;
                        selectedSection = section;

                        break searchForMorph;
                    }
                }
            }

            if (found == null)
            {
                this.copyToRecent(morph);
            }
            else
            {
                this.selected = selectedSection;
                this.scrollTo();

                selectedSection.morph = found;
                selectedSection.category = selectedCategory;
                this.parent.pickMorph(found);
            }
        }
        else
        {
            this.selected = null;
        }
    }

    public void syncSelected()
    {
        AbstractMorph morph = this.getSelected();

        if (morph != null && this.selected != null && this.selected.category != null)
        {
            this.selected.category.edit(morph);
        }
    }

    public AbstractMorph copyToRecent(AbstractMorph morph)
    {
        if (this.selected != null)
        {
            this.selected.reset();
        }

        morph = morph.copy();

        this.user.recent.add(morph);
        this.selected = this.userSection;
        this.selected.morph = morph;
        this.selected.category = this.user.recent;
        this.parent.pickMorph(morph);

        this.scrollTo();

        return morph;
    }

    public boolean isSelectedMorphIsEditable()
    {
        return this.selected != null && this.selected.category != null && this.selected.category.isEditable(this.getSelected());
    }
}
