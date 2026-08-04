package mchorse.blockbuster.client.gui.dashboard.panels.snowstorm;

import java.util.HashMap;
import java.util.Map;

/**
 * Port of Blockbuster 2.7.2's {@code GuiSectionManager} (roadmap P141).
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/snowstorm/GuiSectionManager.java</p>
 *
 * <p>A <b>static</b> per-section-id collapsed-state registry for the Snowstorm
 * editor's collapsible section headers. Two load-bearing quirks preserved from
 * legacy:</p>
 * <ul>
 *   <li>the default state on first query is {@code true} (collapsed) — sections
 *   render collapsed until the user expands them;</li>
 *   <li>the map is {@code static}, so an expand/collapse choice survives scheme
 *   switches <em>and</em> dashboard rebuilds for the whole client session (until
 *   game restart). Do not scope it to a panel instance.</li>
 * </ul>
 */
public class GuiSectionManager
{
    private static final Map<String, Boolean> STATES = new HashMap<String, Boolean>();

    public static boolean isCollapsed(String id)
    {
        Boolean state = STATES.get(id);

        if (state == null)
        {
            state = true; // default value
            STATES.put(id, state);
        }

        return state;
    }

    public static void setCollapsed(String id, boolean collapsed)
    {
        STATES.put(id, collapsed);
    }

    /**
     * This method only adds a state to the Map if the id isn't present
     * @param id
     * @param collapsed
     */
    public static void setDefaultState(String id, boolean collapsed)
    {
        if (!STATES.containsKey(id))
        {
            STATES.put(id, collapsed);
        }
    }
}
