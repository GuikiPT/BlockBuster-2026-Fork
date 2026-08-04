package mchorse.blockbuster.events;

import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.actions.GuiActionPanel;
import mchorse.blockbuster.recording.actions.Action;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.util.function.Consumer;

/**
 * Client extension point letting addons register a {@link GuiActionPanel} for
 * their own {@link Action} classes (port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.events.ActionPanelRegisterEvent}).
 *
 * <p>Legacy fired {@code MinecraftForge.EVENT_BUS.post(new
 * ActionPanelRegisterEvent(this))} at the end of the recording editor's lazy
 * registry build, right after the 18 built-in panels. The Fabric equivalent is
 * the array-backed {@link #EVENT} below — it fires in registration order,
 * matching a priority-less Forge bus.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/events/ActionPanelRegisterEvent.java
 */
public class ActionPanelRegisterEvent
{
    /**
     * Fired once per recording-editor panel, after its built-in action panels
     * are registered.
     */
    public static final Event<Consumer<ActionPanelRegisterEvent>> EVENT =
        EventFactory.createArrayBacked(Consumer.class, listeners -> (event) ->
        {
            for (Consumer<ActionPanelRegisterEvent> listener : listeners)
            {
                listener.accept(event);
            }
        });

    public GuiRecordingEditorPanel panel;

    public ActionPanelRegisterEvent(GuiRecordingEditorPanel panel)
    {
        this.panel = panel;
    }

    public void register(Class<? extends Action> action, GuiActionPanel<? extends Action> panel)
    {
        this.panel.panels.put(action, panel);
    }
}
