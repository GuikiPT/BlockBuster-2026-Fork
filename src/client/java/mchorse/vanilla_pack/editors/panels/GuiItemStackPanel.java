package mchorse.vanilla_pack.editors.panels;

import mchorse.mclib.client.gui.framework.elements.buttons.GuiSlotElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import mchorse.vanilla_pack.morphs.ItemStackMorph;
import net.minecraft.client.MinecraftClient;

/**
 * Item-stack morph panel (roadmap P59.2) — the shared two-widget panel behind
 * the block morph editor: the stack slot and the lighting toggle.
 *
 * <p>Port of Metamorph 1.4's {@code GuiItemStackPanel}. The generic bound is
 * legacy's: the panel edits any {@link ItemStackMorph}, so both
 * {@link mchorse.vanilla_pack.editors.GuiBlockMorph} and (through
 * {@link GuiItemPanel}, which extends the same idea) the item editor can host
 * it.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/panels/GuiItemStackPanel.java
 */
public class GuiItemStackPanel extends GuiMorphPanel<ItemStackMorph, GuiAbstractMorph<? extends ItemStackMorph>>
{
    public GuiSlotElement slot;
    public GuiToggleElement lighting;

    public GuiItemStackPanel(MinecraftClient mc, GuiAbstractMorph<? extends ItemStackMorph> editor)
    {
        super(mc, editor);

        this.slot = new GuiSlotElement(mc, 0, (stack) -> this.morph.setStack(stack));
        this.lighting = new GuiToggleElement(mc, IKey.lang("metamorph.gui.label.lighting"), (b) -> this.morph.lighting = b.isToggled());

        this.slot.flex().relative(this).x(0.5F, 0).y(1, -10).wh(32, 32).anchor(0.5F, 1);
        this.lighting.flex().relative(this).xy(10, 10).w(110);

        this.add(this.slot, this.lighting);
    }

    @Override
    public void fillData(ItemStackMorph morph)
    {
        super.fillData(morph);

        this.slot.setStack(morph.getStack());
        this.lighting.toggled(morph.lighting);
    }
}
