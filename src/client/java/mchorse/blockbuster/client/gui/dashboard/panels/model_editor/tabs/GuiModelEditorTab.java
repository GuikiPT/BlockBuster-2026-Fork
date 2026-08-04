package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs;

import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.GuiModelEditorPanel;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * Base class for the model editor's mutually-exclusive side tabs (roadmap P137).
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/tabs/GuiModelEditorTab.java}.
 * {@code Minecraft} &rarr; {@code MinecraftClient}; the legacy
 * {@code this.font.drawStringWithShadow(...)} title becomes
 * {@link GuiDraw#drawStringWithShadow} at the same {@code (area.x + 4, area.y + 6)}
 * offset and {@code 0xeeeeee} colour.</p>
 */
public abstract class GuiModelEditorTab extends GuiElement
{
    protected IKey title = IKey.EMPTY;
    protected GuiModelEditorPanel panel;

    public GuiModelEditorTab(MinecraftClient mc, GuiModelEditorPanel panel)
    {
        super(mc);

        this.panel = panel;
    }

    public GuiModelEditorPanel getPanel()
    {
        return this.panel;
    }

    @Override
    public void draw(GuiContext context)
    {
        this.drawLabels();
        super.draw(context);
    }

    protected void drawLabels()
    {
        if (this.title != null)
        {
            GuiDraw.drawStringWithShadow(this.font, this.title.get(), this.area.x + 4, this.area.y + 6, 0xeeeeee);
        }
    }
}
