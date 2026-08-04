package mchorse.mclib.events;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code RemoveDashboardPanels} Forge event (roadmap
 * P44) — fired by the {@code KeyboardHandler} session cleanup when a
 * non-{@code GuiBase} screen opens while no world is loaded (i.e. the
 * dashboard singleton got dropped after leaving a world), so subsystems can
 * clear their cached panels. Same callback-list pattern as
 * {@link RegisterDashboardPanels} (see the P22 note there).
 */
public class RemoveDashboardPanels
{
    public static final List<Consumer<RemoveDashboardPanels>> CALLBACKS = new ArrayList<Consumer<RemoveDashboardPanels>>();

    public RemoveDashboardPanels()
    {}

    public static void post(RemoveDashboardPanels event)
    {
        for (Consumer<RemoveDashboardPanels> callback : CALLBACKS)
        {
            callback.accept(event);
        }
    }
}
