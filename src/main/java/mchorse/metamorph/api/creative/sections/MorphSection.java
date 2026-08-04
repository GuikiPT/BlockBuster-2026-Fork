package mchorse.metamorph.api.creative.sections;

import mchorse.metamorph.api.creative.categories.MorphCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Base creative morph section (roadmap P57).
 *
 * <p>Faithful port of legacy {@code MorphSection}: title, category list and a
 * {@code hidden} flag. Legacy {@code getGUI(...)} (which constructs the
 * client-side {@code GuiMorphSection}) is a P58 concern and lives with the GUI
 * classes; it is intentionally omitted from this common data-model class.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/sections/MorphSection.java
 */
public class MorphSection
{
    public String title;
    public List<MorphCategory> categories = new ArrayList<MorphCategory>();
    public boolean hidden;

    public MorphSection(String title)
    {
        this.title = title;
    }

    /**
     * Localized display title ({@code morph.section.<title>}). Client-side use
     * only; headless it resolves to the raw translation key.
     */
    public String getTitle()
    {
        return Text.translatable("morph.section." + this.title).getString();
    }

    public void add(MorphCategory category)
    {
        this.categories.add(category);
    }

    public void remove(MorphCategory category)
    {
        this.categories.remove(category);
    }

    /**
     * This method gets called when a new morph picker appears
     */
    public void update(World world)
    {}

    /**
     * This method gets called when player exits to the main menu
     */
    public void reset()
    {}

    /* SEAM(P58): getGUI(...) constructing GuiMorphSection is client-only and
     * lands with the creative picker GUI. */

    public boolean keyTyped(PlayerEntity player, int keycode)
    {
        for (MorphCategory category : this.categories)
        {
            if (category.keyTyped(player, keycode))
            {
                return true;
            }
        }

        return false;
    }
}
