package mchorse.metamorph.client.gui;

import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.creative.MorphFilter;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.creative.GuiMorphSection;
import mchorse.metamorph.client.gui.creative.MorphGridNavigator;
import mchorse.metamorph.client.gui.creative.MorphPickerLayout;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The generic scrolling multi-section morph list — legacy
 * {@code mchorse.metamorph.client.gui.GuiMorphs}.
 *
 * <p>Legacy split the picker across this base ({@link GuiScrollElement} holding
 * the {@link GuiMorphSection} list, arrow-key grid navigation, the search
 * filter, {@code scrollTo} and the section-height {@code resize} pass) and two
 * subclasses: {@code GuiCreativeMorphs} (the creative picker's section setup
 * from {@code MorphManager}'s {@code MorphList}) and {@code GuiSurvivalMorphs}
 * (the survival menu's single acquired-or-user section). The port folded the
 * base into {@code GuiCreativeMorphs} while only the creative picker existed
 * (P58); it is re-extracted here now that the survival menu (P61) needs it, so
 * the class split matches legacy again and both stay diff-able.</p>
 *
 * <p>Arrow-key grid navigation — legacy {@code pickMorph(int, int)} plus
 * {@code GuiMorphSection.calculateXY}/{@code getMorphAt} — is delegated to the
 * headless {@link MorphGridNavigator}; this class only maps the navigator's
 * (section-index, category, morph) result back onto the concrete
 * {@link GuiMorphSection} and fires its pick callback. Subclasses build the
 * sections and assign {@link #navigator} in their own {@code setupSections}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/GuiMorphs.java
 */
public class GuiMorphs extends GuiScrollElement
{
    /** Cached, already lower-cased+trimmed previous filter (legacy field name). */
    public String filter = "";

    public List<GuiMorphSection> sections = new ArrayList<GuiMorphSection>();
    public GuiMorphSection selected;

    /**
     * The single {@link MorphFilter} instance shared by every section and the
     * navigator, so search/favourite state stays consistent across cells and
     * arrow navigation. Legacy set an identical filter string on each section in
     * a loop; sharing one instance is behaviourally identical and lets the
     * navigator observe the same filter.
     */
    protected final MorphFilter sharedFilter = new MorphFilter();

    /** Built by the subclass' {@code setupSections}; null until then. */
    protected MorphGridNavigator navigator;

    private boolean scrollTo;

    public GuiMorphs(MinecraftClient mc)
    {
        super(mc);

        this.scroll.scrollSpeed = MorphPickerLayout.SCROLL_SPEED;

        IKey category = IKey.lang("metamorph.gui.morphs.keys.category");

        this.keys().register(IKey.lang("metamorph.gui.morphs.keys.down"), LegacyKeyCodes.KEY_DOWN, () -> this.pickMorph(0, 1)).category(category);
        this.keys().register(IKey.lang("metamorph.gui.morphs.keys.up"), LegacyKeyCodes.KEY_UP, () -> this.pickMorph(0, -1)).category(category);
        this.keys().register(IKey.lang("metamorph.gui.morphs.keys.right"), LegacyKeyCodes.KEY_RIGHT, () -> this.pickMorph(1, 0)).category(category);
        this.keys().register(IKey.lang("metamorph.gui.morphs.keys.left"), LegacyKeyCodes.KEY_LEFT, () -> this.pickMorph(-1, 0)).category(category);
    }

    /* Arrow-key grid navigation (legacy GuiMorphs.pickMorph via MorphGridNavigator) */

    protected void pickMorph(int dx, int dy)
    {
        if (this.navigator == null || this.sections.isEmpty())
        {
            return;
        }

        /* Sync the navigator's cursor from the current (possibly click-driven)
         * selection so its calculateXY starts from the right place. */
        if (this.selected == null || this.selected.morph == null)
        {
            this.navigator.resetSelected();
        }
        else
        {
            this.navigator.setSelected(this.sections.indexOf(this.selected), this.selected.category, this.selected.morph);
        }

        AbstractMorph result = this.navigator.pickMorph(dx, dy);

        if (result == null)
        {
            return;
        }

        GuiMorphSection target = this.sections.get(this.navigator.getSectionIndex());

        if (this.selected != null && this.selected != target)
        {
            this.selected.reset();
        }

        this.selected = target;
        target.morph = result;
        target.category = this.navigator.getCategory();
        target.pick(result, this.navigator.getCategory());

        this.scrollTo();
    }

    /* Morph selection */

    public void setSelected(AbstractMorph morph)
    {
        this.resetSelected();
    }

    public void setSelectedDirect(GuiMorphSection selected)
    {
        this.setSelectedDirect(selected, selected.morph, selected.category);
    }

    public void setSelectedDirect(GuiMorphSection section, AbstractMorph morph, MorphCategory category)
    {
        this.resetSelected();

        this.selected = section;
        this.selected.set(morph, category);
    }

    public void resetSelected()
    {
        if (this.selected != null)
        {
            this.selected.reset();
        }
    }

    public AbstractMorph getSelected()
    {
        return this.selected == null ? null : this.selected.morph;
    }

    /* Filtering */

    /**
     * Set the search filter. Pre-normalises (lower-case + trim) exactly like
     * legacy {@code GuiMorphs.setFilter} and short-circuits on an unchanged
     * value. Since every section shares {@link #sharedFilter}, one update covers
     * the cells and the navigator; the per-section loop is kept for parity.
     */
    public void setFilter(String filter)
    {
        if (filter.equals(this.filter))
        {
            return;
        }

        String lcfilter = filter.toLowerCase().trim();

        for (GuiMorphSection section : this.sections)
        {
            section.setFilter(lcfilter);
        }

        this.filter = lcfilter;
    }

    public void setFavorite(boolean favorite)
    {
        for (GuiMorphSection section : this.sections)
        {
            section.setFavorite(favorite);
        }
    }

    /* Scrolling */

    /**
     * Scroll the selected morph into view. Deferred until the first layout pass
     * when {@code area.w == 0} (pre-first-resize), or the initial selection jumps
     * break. Relies on the section heights computed by {@link #resize()}.
     */
    public void scrollTo()
    {
        if (this.area.w == 0)
        {
            this.scrollTo = true;

            return;
        }

        AbstractMorph morph = this.getSelected();

        if (morph == null)
        {
            return;
        }

        int y = 0;

        for (GuiMorphSection section : this.sections)
        {
            if (section.morph == morph)
            {
                this.scroll.scrollIntoView(y + section.getY(section.morph), section.cellHeight + MorphPickerLayout.LAST_SECTION_TAIL);

                break;
            }

            y += section.getFullHeight();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        for (GuiMorphSection section : this.sections)
        {
            section.flex().h(section.getFullHeight());
        }

        super.resize();

        if (this.navigator != null)
        {
            this.navigator.onLayout(this.sections.isEmpty() ? 0 : this.sections.get(0).getPerRow());
        }

        if (this.scrollTo)
        {
            this.scrollTo = false;
            this.scrollTo();
        }
    }
}
