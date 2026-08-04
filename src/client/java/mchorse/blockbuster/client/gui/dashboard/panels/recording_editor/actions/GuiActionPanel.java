package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — base class of the per-action editor sub-panels.
 *
 * <p>Faithful port of legacy
 * {@code blockbuster-1.12/.../recording_editor/actions/GuiActionPanel.java}.
 * {@link #fill(Action)} resolves the registry <em>name</em> of the action
 * class through {@link ActionRegistry#NAME_TO_CLASS} inverse lookup and points
 * the title/description lang keys at
 * {@code blockbuster.gui.record_editor.actions.<name>.title}/{@code .desc}. The
 * description is word-wrapped at one-third of the panel width. The tooltip is
 * hidden in the constructor. {@link #disappear()} and {@link #setMorph} are
 * no-op hooks overridden only by the morph panel.</p>
 */
public abstract class GuiActionPanel<T extends Action> extends GuiElement
{
    public T action;
    public GuiRecordingEditorPanel panel;

    private IKey title = IKey.lang("");
    private IKey description = IKey.lang("");

    public GuiActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc);

        this.panel = panel;

        this.hideTooltip();
    }

    public void fill(T action)
    {
        this.action = action;

        String key = ActionRegistry.NAME_TO_CLASS.inverse().get(action.getClass());

        if (key != null)
        {
            this.setKey(key);
        }
    }

    public void disappear()
    {}

    public void setMorph(AbstractMorph morph)
    {}

    public void setKey(String key)
    {
        this.title.set("blockbuster.gui.record_editor.actions." + key + ".title");
        this.description.set("blockbuster.gui.record_editor.actions." + key + ".desc");
    }

    @Override
    public void draw(GuiContext context)
    {
        String title = this.title.get();

        if (!title.isEmpty())
        {
            GuiDraw.drawStringWithShadow(this.font, title, this.area.x + 10, this.area.y + 10, 0xffffff);
            GuiDraw.drawMultiText(this.font, this.description.get(), this.area.x + 10, this.area.y + 30, 0xcccccc, this.area.w / 3);
        }

        super.draw(context);
    }
}
