package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — fallback panel for field-less actions ({@code SwipeAction},
 * {@code CloseContainerAction}, {@code ShootGunAction}) and unknown classes.
 * Draws the centered {@code blockbuster.gui.record_editor.no_fields} label.
 */
public class GuiEmptyActionPanel extends GuiActionPanel<Action>
{
    public GuiEmptyActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);
    }

    @Override
    public void draw(GuiContext context)
    {
        super.draw(context);

        GuiDraw.drawCenteredString(this.font, IKey.lang("blockbuster.gui.record_editor.no_fields").get(), this.area.mx(), this.area.my(), 0xffffff);
    }
}
