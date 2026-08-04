package mchorse.vanilla_pack.editors;

import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.util.MMIcons;
import mchorse.vanilla_pack.editors.panels.GuiItemPanel;
import mchorse.vanilla_pack.morphs.ItemMorph;
import net.minecraft.client.MinecraftClient;

/**
 * Item morph editor (roadmap P59.2) — one panel, {@link GuiItemPanel}.
 *
 * <p>Port of Metamorph 1.4's {@code GuiItemMorph}. Registered <b>before</b>
 * {@link GuiBlockMorph} so that neither can shadow the other: {@link ItemMorph}
 * and {@code BlockMorph} are siblings under {@code ItemStackMorph}, and each
 * editor's {@code canEdit} tests its own concrete class.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/GuiItemMorph.java
 */
public class GuiItemMorph extends GuiAbstractMorph<ItemMorph>
{
    public GuiItemPanel item;

    public GuiItemMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.item = new GuiItemPanel(mc, this);
        this.registerPanel(this.item, IKey.lang("metamorph.gui.editor.item_morph"), MMIcons.ITEM);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof ItemMorph;
    }
}
