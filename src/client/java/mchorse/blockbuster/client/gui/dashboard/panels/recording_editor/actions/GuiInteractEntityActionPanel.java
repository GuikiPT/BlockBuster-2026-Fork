package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.InteractEntityAction;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — {@code InteractEntityAction} reuses the hand circulate of
 * {@link GuiItemUseActionPanel} verbatim (empty subclass, mirroring legacy).
 */
public class GuiInteractEntityActionPanel extends GuiItemUseActionPanel<InteractEntityAction>
{
    public GuiInteractEntityActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);
    }
}
