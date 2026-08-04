package mchorse.metamorph.api.creative;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NbtCompound;

/**
 * Headless-testable search/filter logic for the creative picker (roadmap P58).
 *
 * <p>Extracted, GL-free core of the legacy client class
 * {@code GuiMorphSection}: the {@code favorite}/{@code filter} matching rule
 * ({@link #isMatching(AbstractMorph)} / {@link #noFilter()}) and the
 * {@code getMorphCommand} string builder. The P58 client
 * {@code GuiMorphSection} (which owns cell layout, rendering, context menus and
 * grid navigation — none of which is headless-testable) delegates its filter
 * decisions and its {@code /morph} command string to this class.</p>
 *
 * <p>Semantics preserved from legacy:</p>
 * <ul>
 *   <li>The filter string is lowercased and trimmed on assignment (legacy
 *       {@code GuiMorphs#setFilter} pre-normalizes before handing it to each
 *       section).</li>
 *   <li>Favorite-only mode <b>overrides</b> the text filter entirely — while
 *       {@code favorite} is set, only {@code morph.favorite} morphs match.</li>
 *   <li>Text matching is a {@code contains} over both {@code morph.name} and the
 *       display name, both lowercased.</li>
 *   <li>{@code getMorphCommand} omits the {@code Name} tag from the NBT payload
 *       because the name is the command's second argument.</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/creative/GuiMorphSection.java
 */
public class MorphFilter
{
    public boolean favorite;

    private String filter = "";

    /**
     * Build the {@code /morph @p <name> <nbt>} command for a morph, omitting the
     * {@code Name} tag from the NBT (it is the second argument). Returns an
     * empty string for a {@code null} morph, matching legacy.
     */
    public static String getMorphCommand(AbstractMorph morph)
    {
        if (morph != null)
        {
            NbtCompound nbt = morph.toNBT();

            nbt.remove("Name");

            return "/morph @p " + morph.name + " " + nbt.toString();
        }

        return "";
    }

    public String getFilter()
    {
        return this.filter;
    }

    public void setFilter(String filter)
    {
        this.filter = filter == null ? "" : filter.toLowerCase().trim();
    }

    public boolean noFilter()
    {
        return this.filter.isEmpty() && !this.favorite;
    }

    public boolean isMatching(AbstractMorph morph)
    {
        if (this.favorite)
        {
            return morph.favorite;
        }

        if (this.filter.isEmpty())
        {
            return true;
        }

        return morph.name.toLowerCase().contains(this.filter)
            || morph.getDisplayName().toLowerCase().contains(this.filter);
    }
}
