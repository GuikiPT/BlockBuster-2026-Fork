package mchorse.metamorph.client.gui.survival;

import mchorse.metamorph.Metamorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.MorphList;
import mchorse.metamorph.api.creative.categories.AcquiredCategory;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.creative.sections.UserSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.client.gui.GuiMorphs;
import mchorse.metamorph.client.gui.creative.GuiMorphSection;
import mchorse.metamorph.client.gui.creative.GuiUserSection;
import mchorse.metamorph.client.gui.creative.MorphGridNavigator;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * The survival menu's morph list (roadmap P61) — 1:1 port of Metamorph 1.4's
 * {@code mchorse.metamorph.client.gui.survival.GuiSurvivalMorphs}.
 *
 * <p>Unlike the creative picker this shows exactly <b>one</b> section, and
 * which one depends on the visibility rule pinned by
 * {@link SurvivalScreenLogic#showWholeUserSection(boolean, boolean)}: in
 * creative, or with {@code allowMorphingIntoCategoryMorphs} on, it is the real
 * {@link UserSection} (recent + acquired + every custom global category); with
 * the rule off it is a throwaway section holding a single
 * {@link AcquiredCategory} filled from the player's acquired morphs, so a
 * survival player can only reach what they have actually acquired.</p>
 *
 * <p>{@link #acquired} is the category the screen's index-based packets address
 * — {@link #isAcquiredSelected()} is what routes a button press between an
 * index packet and a full-NBT one, and {@link #setSelected(AbstractMorph)}
 * remaps the incoming selection onto its {@code equals}-equal instance inside
 * that category so {@code indexOf} resolves against the acquired list rather
 * than the picker's copy (P47 equals parity).</p>
 *
 * <p>Boundary changes: {@code Minecraft} &rarr; {@code MinecraftClient}; legacy
 * dispatched section GUI construction through {@code MorphSection.getGUI},
 * which cannot exist here because {@code MorphSection} lives in the common
 * source set — the {@link UserSection} &rarr; {@link GuiUserSection} dispatch is
 * done inline, the same way {@code GuiCreativeMorphs} does it.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/client/gui/survival/GuiSurvivalMorphs.java
 */
public class GuiSurvivalMorphs extends GuiMorphs
{
    public AcquiredCategory acquired;

    public GuiSurvivalMorphs(MinecraftClient mc)
    {
        super(mc);
    }

    public void setupSections(boolean creative, Consumer<GuiMorphSection> callback)
    {
        MorphList list = MorphManager.INSTANCE.list;
        IMorphing cap = this.mc == null || this.mc.player == null ? null : Morphing.get(this.mc.player);

        MorphSection section;
        AcquiredCategory category;
        UserSection user = SurvivalScreenLogic.showWholeUserSection(creative, Metamorph.allowMorphingIntoCategoryMorphs.get())
            ? userSection(list)
            : null;

        if (user != null)
        {
            section = user;
            section.update(this.mc == null ? null : this.mc.world);
            category = user.acquired;
        }
        else
        {
            section = new MorphSection("user");
            category = new AcquiredCategory(section, "acquired");

            category.setMorphs(cap == null ? Collections.emptyList() : cap.getAcquiredMorphs());
            section.add(category);
        }

        GuiMorphSection element = section instanceof UserSection
            ? new GuiUserSection(this.mc, null, section, callback)
            : new GuiMorphSection(this.mc, null, section, callback);

        element.filter = this.sharedFilter;
        element.flex();

        this.removeAll();
        this.add(element);
        this.selected = element;
        this.acquired = category;

        this.sections.clear();
        this.sections.add(this.selected);

        this.navigator = new MorphGridNavigator(Collections.singletonList(section), this.sharedFilter);
    }

    /**
     * Legacy took {@code list.sections.get(0)} and cast it to
     * {@link UserSection}. Kept as a lookup rather than a cast so a morph list
     * whose first section is not the user one (an addon inserting ahead of it,
     * or an empty list in a headless test) falls back to the acquired-only
     * branch instead of throwing.
     */
    private static UserSection userSection(MorphList list)
    {
        if (list == null)
        {
            return null;
        }

        for (MorphSection section : list.sections)
        {
            if (section instanceof UserSection)
            {
                return (UserSection) section;
            }
        }

        return null;
    }

    @Override
    public void setSelected(AbstractMorph morph)
    {
        super.setSelected(morph);

        if (morph != null && this.selected != null)
        {
            AbstractMorph found = this.acquired == null ? null : this.acquired.getEqual(morph);

            if (found != null)
            {
                this.selected.category = this.acquired;
                this.selected.morph = found;
            }
        }
    }

    /**
     * Whether the current selection lives in the acquired category — the flag
     * that routes every button press between an index-based packet and a
     * full-NBT one.
     */
    public boolean isAcquiredSelected()
    {
        return this.selected != null && this.selected.category == this.acquired;
    }
}
