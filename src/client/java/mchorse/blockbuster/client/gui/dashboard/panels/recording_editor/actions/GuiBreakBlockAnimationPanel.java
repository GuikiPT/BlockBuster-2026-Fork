package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.BreakBlockAnimation;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — {@link GuiBlockActionPanel} plus an integer {@code progress} trackpad
 * (0..100) bound to {@link BreakBlockAnimation#progress}. (Field kept named
 * {@code charge} to mirror the legacy source for diff-ability.)
 */
public class GuiBreakBlockAnimationPanel extends GuiBlockActionPanel<BreakBlockAnimation>
{
    public GuiTrackpadElement charge;

    public GuiBreakBlockAnimationPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.charge = new GuiTrackpadElement(mc, (charge) -> this.action.progress = charge.intValue());
        this.charge.tooltip(IKey.lang("blockbuster.gui.record_editor.progress"));
        this.charge.limit(0, 100, true);
        this.charge.flex().set(0, -25, 100, 20).relative(this.x.resizer());

        this.add(this.charge);
    }

    @Override
    public void fill(BreakBlockAnimation action)
    {
        super.fill(action);

        this.charge.setValue(action.progress);
    }
}
