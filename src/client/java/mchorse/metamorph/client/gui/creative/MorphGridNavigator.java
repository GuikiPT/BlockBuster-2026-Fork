package mchorse.metamorph.client.gui.creative;

import mchorse.metamorph.api.creative.MorphFilter;
import mchorse.metamorph.api.creative.categories.MorphCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;

import java.util.List;
import java.util.function.Consumer;

/**
 * Headless, GL-free core of the creative picker's arrow-key grid navigation
 * (roadmap P58).
 *
 * <p>This is the extracted, testable logic of legacy {@code GuiMorphs} +
 * {@code GuiMorphSection} — the virtual {@code x}/{@code y} cursor that hops
 * across categories and sections, skips empty/filtered categories, wraps
 * {@code x} at row edges and clamps overshoot to the last morph. Cell drawing,
 * hover, context menus and the actual scroll animation stay with the GL
 * {@code GuiMorphSection} screen, which delegates its cursor walk to an instance
 * of this class.</p>
 *
 * <p>Legacy quirks reproduced verbatim (they are load-bearing — see the plan's
 * P58 "Quirks &amp; gotchas"):</p>
 * <ul>
 *   <li>{@code getMorphAt}'s overshoot shortcut clamps to the category's last
 *       <b>raw</b> morph ({@code getMorphs().get(size - 1)}), not the last
 *       filtered one.</li>
 *   <li>{@code x} is wrapped in place: {@code x < 0 -> row - 1},
 *       {@code x > row - 1 -> 0}.</li>
 *   <li>a vertical move of {@code 0} is forced to {@code 1} once the in-category
 *       lookup misses, so left/right at a category edge still crosses rows.</li>
 *   <li>{@code scrollTo} is deferred until the first layout pass when the
 *       viewport width is still zero (here {@link #laidOut}).</li>
 *   <li>section crossing uses an empty sentinel category so a section with no
 *       categories is skipped without indexing past its (empty) list.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/GuiMorphs.java
 *                .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiMorphSection.java
 */
public class MorphGridNavigator
{
    /** Legacy sentinel used while walking across sections with no categories. */
    private static final MorphCategory EMPTY_CATEGORY = new MorphCategory(null, null);

    /** The registered sections, in draw/registration order. */
    public final List<MorphSection> sections;

    /** Shared filter (search text + favourite-only) driving cell visibility. */
    public final MorphFilter filter;

    /** Optional hook fired on every successful pick (the selected morph). */
    public Consumer<AbstractMorph> pickCallback;

    /** Cells per row; legacy {@code area.w / cellWidth} clamped to &ge; 1. */
    public int perRow = 1;

    /** Analog of legacy {@code area.w != 0} — set once the picker is laid out. */
    public boolean laidOut;

    /** Set true whenever a pick asks the view to scroll the selection in. */
    public boolean scrollToRequested;

    /** Number of successful picks (for headless observation). */
    public int pickCount;

    private boolean scrollToDeferred;

    /* Current selection / cursor state. */
    private int sectionIndex = -1;
    private MorphCategory category;
    private AbstractMorph morph;
    private int x;
    private int y;

    public MorphGridNavigator(List<MorphSection> sections, MorphFilter filter)
    {
        this.sections = sections;
        this.filter = filter;
    }

    public AbstractMorph getSelected()
    {
        return this.morph;
    }

    public MorphCategory getCategory()
    {
        return this.category;
    }

    public int getSectionIndex()
    {
        return this.sectionIndex;
    }

    public int getX()
    {
        return this.x;
    }

    public int getY()
    {
        return this.y;
    }

    /**
     * Directly seed the current selection (legacy {@code setSelectedDirect}) so
     * arrow navigation has a starting point.
     */
    public void setSelected(int sectionIndex, MorphCategory category, AbstractMorph morph)
    {
        this.sectionIndex = sectionIndex;
        this.category = category;
        this.morph = morph;
    }

    public void resetSelected()
    {
        this.sectionIndex = -1;
        this.category = null;
        this.morph = null;
    }

    /**
     * Called when the picker is (re)laid out. Sets {@link #perRow}, marks the
     * navigator laid out and flushes a deferred {@code scrollTo}.
     */
    public void onLayout(int perRow)
    {
        this.perRow = Math.max(perRow, 1);
        this.laidOut = perRow > 0;

        if (this.laidOut && this.scrollToDeferred)
        {
            this.scrollToDeferred = false;
            this.scrollTo();
        }
    }

    /**
     * Move the cursor by {@code (dx, dy)} and pick the morph it lands on. Faithful
     * port of legacy {@code GuiMorphs.pickMorph}. Returns the newly-selected
     * morph, or {@code null} when the move is blocked (e.g. up past the first
     * category / down past the last).
     */
    public AbstractMorph pickMorph(int dx, int dy)
    {
        if (this.sections.isEmpty())
        {
            return null;
        }

        if (this.morph == null)
        {
            dx = 0;
            dy = 0;
            this.sectionIndex = 0;
            this.category = firstCategoryOf(this.sections.get(0));
            this.x = this.y = 0;

            if (this.category == null)
            {
                return null;
            }
        }
        else
        {
            this.calculateXY();
        }

        int ox = this.x;

        this.x += dx;
        this.y += dy;

        AbstractMorph found = this.getMorphAt();

        if (found != null)
        {
            this.morph = found;
            this.pick(this.morph);
            this.scrollTo();

            return found;
        }

        if (dy < 0 && this.isFirstCategory())
        {
            return null;
        }
        else if (dy > 0 && this.isLastCategory())
        {
            return null;
        }

        if (dy == 0)
        {
            dy = 1;
        }

        int section = this.sectionIndex;
        MorphCategory cat = this.category;

        do
        {
            int index = this.sections.get(section).categories.indexOf(cat) + dy;

            if (index < 0)
            {
                int si = section - 1;

                if (si < 0)
                {
                    return null;
                }

                section = si;

                List<MorphCategory> cats = this.sections.get(section).categories;
                cat = cats.isEmpty() ? EMPTY_CATEGORY : cats.get(cats.size() - 1);
            }
            else if (index >= this.sections.get(section).categories.size())
            {
                int si = section + 1;

                if (si >= this.sections.size())
                {
                    return null;
                }

                section = si;

                List<MorphCategory> cats = this.sections.get(section).categories;
                cat = cats.isEmpty() ? EMPTY_CATEGORY : cats.get(0);
            }
            else
            {
                cat = this.sections.get(section).categories.get(index);
            }
        }
        while (this.isCategoryEmpty(cat));

        if (cat.getMorphs().isEmpty())
        {
            return null;
        }

        this.sectionIndex = section;
        this.category = cat;
        this.morph = this.getFirstMorph(ox, dy);
        this.pick(this.morph);
        this.scrollTo();

        return this.morph;
    }

    /* Internal helpers, mirroring GuiMorphSection */

    private void calculateXY()
    {
        int j = 0;

        for (AbstractMorph m : this.category.getMorphs())
        {
            if (m == this.morph)
            {
                int row = this.perRow;

                this.x = j % row;
                this.y = j / row;

                return;
            }

            if (this.filter.isMatching(m))
            {
                j++;
            }
        }
    }

    private AbstractMorph getMorphAt()
    {
        int row = this.perRow;
        List<AbstractMorph> morphs = this.category.getMorphs();
        int size = morphs.size();

        /* Shortcuts */
        if (this.y < 0 || size == 0)
        {
            return null;
        }
        else if (this.y < size / row + 1)
        {
            int i = this.x + this.y * row;

            if (i >= size)
            {
                return morphs.get(size - 1);
            }
        }

        /* Find the actual morph */
        int j = 0;

        if (this.x < 0) this.x = row - 1;
        if (this.x > row - 1) this.x = 0;

        for (AbstractMorph m : morphs)
        {
            if (this.filter.isMatching(m))
            {
                if (j % row == this.x && j / row == this.y)
                {
                    return m;
                }

                j++;
            }
        }

        return null;
    }

    private AbstractMorph getFirstMorph(int ox, int dy)
    {
        List<AbstractMorph> list = this.category.getMorphs();
        AbstractMorph first = null;

        int row = this.perRow;
        int j = 0;
        int c = this.getMorphsSize(this.category);
        int firstIndex = dy < 0 ? c - 1 : 0;
        int lastIndex = dy < 0 ? (c - 1) / row * row + ox : ox;

        for (AbstractMorph m : list)
        {
            if (this.filter.isMatching(m))
            {
                if (j == firstIndex)
                {
                    first = m;
                }

                if (j == lastIndex)
                {
                    return m;
                }

                j++;
            }
        }

        return first;
    }

    private int getMorphsSize(MorphCategory category)
    {
        if (this.filter.noFilter())
        {
            return category.getMorphs().size();
        }

        int count = 0;

        for (AbstractMorph m : category.getMorphs())
        {
            count += this.filter.isMatching(m) ? 1 : 0;
        }

        return count;
    }

    private boolean isCategoryEmpty(MorphCategory category)
    {
        if (category.getMorphs().isEmpty())
        {
            return true;
        }

        return this.getMorphsSize(category) == 0;
    }

    private boolean isFirstCategory()
    {
        List<MorphCategory> cats = this.sections.get(0).categories;

        return this.sectionIndex == 0
            && !cats.isEmpty()
            && this.category == cats.get(0);
    }

    private boolean isLastCategory()
    {
        List<MorphCategory> cats = this.sections.get(this.sectionIndex).categories;

        return this.sectionIndex == this.sections.size() - 1
            && !cats.isEmpty()
            && this.category == cats.get(cats.size() - 1);
    }

    private static MorphCategory firstCategoryOf(MorphSection section)
    {
        return section.categories.isEmpty() ? null : section.categories.get(0);
    }

    private void pick(AbstractMorph morph)
    {
        this.pickCount++;

        if (this.pickCallback != null)
        {
            this.pickCallback.accept(morph);
        }
    }

    private void scrollTo()
    {
        if (!this.laidOut)
        {
            this.scrollToDeferred = true;

            return;
        }

        this.scrollToRequested = true;
    }
}
