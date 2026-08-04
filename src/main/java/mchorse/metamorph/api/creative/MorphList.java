package mchorse.metamorph.api.creative;

import mchorse.metamorph.api.creative.sections.MorphSection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Global creative morph list (roadmap P57).
 *
 * <p>Holds every registered {@link MorphSection}. Faithful port of legacy
 * {@code mchorse.metamorph.api.creative.MorphList}: sections are updated on
 * every picker open, reset on disconnect, and the keybind walk delegates
 * section → category → morph in registration order.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/MorphList.java
 */
public class MorphList
{
    /**
     * Morph sections
     */
    public List<MorphSection> sections = new ArrayList<MorphSection>();

    /**
     * Add a morph section to this list
     */
    public void register(MorphSection section)
    {
        this.sections.add(section);
    }

    /**
     * This method gets called when a new morph picker appears
     */
    public void update(World world)
    {
        for (MorphSection section : this.sections)
        {
            section.update(world);
        }
    }

    /**
     * This method gets called when player exits to the main menu
     */
    public void reset()
    {
        for (MorphSection section : this.sections)
        {
            section.reset();
        }
    }

    /**
     * If any of the morphs have keybind attached, use it
     */
    public boolean keyTyped(PlayerEntity player, int keycode)
    {
        for (MorphSection section : this.sections)
        {
            if (section.keyTyped(player, keycode))
            {
                return true;
            }
        }

        return false;
    }

    public <T> T getSection(Class<T> clazz)
    {
        for (MorphSection section : this.sections)
        {
            if (section.getClass() == clazz)
            {
                return clazz.cast(section);
            }
        }

        return null;
    }
}
