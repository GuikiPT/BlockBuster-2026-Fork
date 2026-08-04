package mchorse.mclib.events;

import mchorse.mclib.client.gui.mclib.GuiAbstractDashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code RegisterDashboardPanels} Forge event
 * (roadmap P44). Fired when a {@code GuiAbstractDashboard} is constructed so
 * other bundled subsystems (Blockbuster S12, Aperture S15) can register
 * their dashboard panels.
 *
 * <p>Forge's {@code McLib.EVENT_BUS.post(event)} becomes the same
 * callback-list pattern as {@code ConfigManager.REGISTER_CALLBACKS} (S1 P22
 * note: if P22 later formalizes an event bus replacement, migrate
 * {@link #CALLBACKS} there). Lives in the client source set because the
 * event payload is a client class — legacy shipped it in the common jar but
 * only client code could ever construct/observe it.</p>
 */
public class RegisterDashboardPanels
{
    /**
     * Subscribers, invoked in registration order right after the built-in
     * panels are registered (legacy {@code @SubscribeEvent} order was
     * bus-defined; registration order is deterministic and matches the
     * legacy mod-loading order for the bundled subsystems).
     */
    public static final List<Consumer<RegisterDashboardPanels>> CALLBACKS = new ArrayList<Consumer<RegisterDashboardPanels>>();

    public final GuiAbstractDashboard dashboard;

    public RegisterDashboardPanels(GuiAbstractDashboard dashboard)
    {
        this.dashboard = dashboard;
    }

    /**
     * The port's {@code McLib.EVENT_BUS.post(...)}.
     */
    public static void post(RegisterDashboardPanels event)
    {
        for (Consumer<RegisterDashboardPanels> callback : CALLBACKS)
        {
            callback.accept(event);
        }
    }
}
