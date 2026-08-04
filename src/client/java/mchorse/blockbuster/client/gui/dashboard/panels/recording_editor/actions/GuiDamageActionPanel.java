package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.recording.actions.DamageAction;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P139 — a single non-negative {@code damage} trackpad. The same panel is
 * registered for both {@code AttackAction} and {@code DamageAction} (they share
 * the {@code damage} field).
 *
 * <p>Legacy quirk (reproduced): the callback truncates via {@code intValue()}
 * even though {@code damage} is conceptually a float — dragging the trackpad
 * can only produce integral damage values.</p>
 */
public class GuiDamageActionPanel extends GuiActionPanel<DamageAction>
{
    public GuiTrackpadElement damage;

    public GuiDamageActionPanel(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc, panel);

        this.damage = new GuiTrackpadElement(mc, (charge) -> this.action.damage = charge.intValue());
        this.damage.tooltip(IKey.lang("blockbuster.gui.record_editor.damage"));
        this.damage.min = 0;
        this.damage.flex().set(10, 0, 100, 20).relative(this.area).y(1, -30);

        this.add(this.damage);
    }

    @Override
    public void fill(DamageAction action)
    {
        super.fill(action);

        this.damage.setValue(action.damage);
    }
}
