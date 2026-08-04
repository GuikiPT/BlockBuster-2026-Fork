package mchorse.vanilla_pack.editors;

import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.client.gui.editor.GuiAbstractMorph;
import mchorse.metamorph.util.MMIcons;
import mchorse.vanilla_pack.editors.panels.GuiLabelPanel;
import mchorse.vanilla_pack.morphs.LabelMorph;
import net.minecraft.client.MinecraftClient;

/**
 * Label morph editor (roadmap P59.2) — one panel, {@link GuiLabelPanel}.
 *
 * <p>Port of Metamorph 1.4's {@code GuiLabelMorph}.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/editors/GuiLabelMorph.java
 */
public class GuiLabelMorph extends GuiAbstractMorph<LabelMorph>
{
    public GuiLabelPanel label;

    public GuiLabelMorph(MinecraftClient mc)
    {
        super(mc);

        this.defaultPanel = this.label = new GuiLabelPanel(mc, this);
        this.registerPanel(this.label, IKey.lang("metamorph.gui.label.label"), MMIcons.LABEL);
    }

    @Override
    public boolean canEdit(AbstractMorph morph)
    {
        return morph instanceof LabelMorph;
    }
}
