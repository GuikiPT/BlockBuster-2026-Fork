package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.ItemUseAction;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiCirculateElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

/**
 * P139 — a circulate toggling {@link ItemUseAction#hand} between main/off hand.
 * Yarn's {@link Hand} declares MAIN_HAND(0), OFF_HAND(1), so the ordinal maps
 * 1:1 to the two labels. {@link GuiInteractEntityActionPanel} is an empty
 * subclass reusing this panel.
 */
public class GuiItemUseActionPanel<T extends ItemUseAction> extends GuiActionPanel<T>
{
    public GuiCirculateElement hand;

    public GuiItemUseActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.hand = new GuiCirculateElement(mc, (b) -> this.action.hand = Hand.values()[this.hand.getValue()]);
        this.hand.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.main_hand"));
        this.hand.addLabel(IKey.lang("blockbuster.gui.record_editor.actions.equip.off_hand"));
        this.hand.flex().set(10, 0, 80, 20).relative(this.area).y(1, -30);

        this.add(this.hand);
    }

    @Override
    public void fill(T action)
    {
        super.fill(action);

        this.hand.setValue(action.hand.ordinal());
    }
}
