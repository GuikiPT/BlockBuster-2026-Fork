package mchorse.metamorph.api.creative.categories;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.creative.CreativeMorphNetwork;
import mchorse.metamorph.api.creative.sections.MorphSection;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base creative morph category (roadmap P57).
 *
 * <p>Faithful port of legacy {@code MorphCategory}:</p>
 *
 * <ul>
 *   <li>{@code add} applies the blacklist filter <b>and</b> active settings at
 *       insertion time — a morph added while its name is blacklisted is
 *       silently dropped.</li>
 *   <li>{@code sort} orders morphs case-insensitively by {@code name}.</li>
 *   <li>{@code getEqual} is an {@code equals}-based lookup.</li>
 *   <li>{@code keyTyped} walks the morphs and morphs on the first whose stored
 *       {@code keybind} matches (the keybind walk consumed by P60).</li>
 * </ul>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/api/creative/categories/MorphCategory.java
 */
public class MorphCategory
{
    public MorphSection parent;

    public String title;
    public boolean hidden;
    protected List<AbstractMorph> morphs = new ArrayList<AbstractMorph>();

    public MorphCategory(MorphSection parent, String title)
    {
        this.parent = parent;
        this.title = title;
    }

    /**
     * Localized display title ({@code morph.category.<title>}). Client-side use
     * only; headless it resolves to the raw translation key.
     */
    public String getTitle()
    {
        return Text.translatable("morph.category." + this.title).getString();
    }

    public List<AbstractMorph> getMorphs()
    {
        return this.morphs;
    }

    public boolean isHidden()
    {
        return this.morphs.isEmpty() || this.hidden;
    }

    public AbstractMorph getEqual(AbstractMorph morph)
    {
        for (AbstractMorph child : this.morphs)
        {
            if (child.equals(morph))
            {
                return child;
            }
        }

        return null;
    }

    public void clear()
    {
        this.morphs.clear();
    }

    public void sort()
    {
        Collections.sort(this.morphs, (a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    public final void add(AbstractMorph morph)
    {
        if (MorphManager.isBlacklisted(morph.name))
        {
            return;
        }

        MorphManager.INSTANCE.applySettings(morph);

        this.addMorph(morph);
    }

    protected void addMorph(AbstractMorph morph)
    {
        this.morphs.add(morph);
    }

    public boolean isEditable(AbstractMorph morph)
    {
        return false;
    }

    public void edit(AbstractMorph morph)
    {}

    public boolean remove(AbstractMorph morph)
    {
        return this.morphs.remove(morph);
    }

    public boolean keyTyped(PlayerEntity player, int keycode)
    {
        for (AbstractMorph morph : this.morphs)
        {
            if (morph.keybind == keycode && this.morph(player, morph))
            {
                return true;
            }
        }

        return false;
    }

    protected boolean morph(PlayerEntity player, AbstractMorph morph)
    {
        /* SEAM(P54/P60): legacy checks Metamorph.proxy.canUse then sends
         * PacketMorph. Routed through the network seam. */
        if (CreativeMorphNetwork.INSTANCE.canUse(player))
        {
            CreativeMorphNetwork.INSTANCE.morph(morph);

            return true;
        }

        return false;
    }
}
