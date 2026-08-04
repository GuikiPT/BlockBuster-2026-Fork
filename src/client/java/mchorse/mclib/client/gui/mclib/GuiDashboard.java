package mchorse.mclib.client.gui.mclib;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.gui.GuiConfigPanel;
import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiDashboard} (roadmap P44) — the concrete
 * mclib dashboard: config panel (default), math-graph playground, and the
 * debug panel when {@code McLib.debugPanel} is set (invisible config value,
 * JSON-edit only — exactly like 1.12.2).
 *
 * <p>The {@link #dashboard} singleton is cached per world session; the
 * {@code KeyboardHandler} clears it when a non-{@code GuiBase} screen opens
 * with no world loaded, and {@link #get()} recreates it after that.</p>
 */
public class GuiDashboard extends GuiAbstractDashboard
{
    public static GuiDashboard dashboard;

    public GuiConfigPanel config;

    public static GuiDashboard get()
    {
        if (dashboard == null)
        {
            dashboard = new GuiDashboard(MinecraftClient.getInstance());
        }

        return dashboard;
    }

    public GuiDashboard(MinecraftClient mc)
    {
        super(mc);

        this.panels.registerPanel(new GuiGraphPanel(mc, this), IKey.lang("mclib.gui.graph.tooltip"), Icons.GRAPH);
    }

    @Override
    protected GuiDashboardPanels createDashboardPanels(MinecraftClient mc)
    {
        return new GuiDashboardPanels(mc);
    }

    @Override
    protected void registerPanels(MinecraftClient mc)
    {
        this.panels.registerPanel(this.config = new GuiConfigPanel(mc, this), IKey.lang("mclib.gui.config.tooltip"), Icons.GEAR);
        this.defaultPanel = this.config;

        if (McLib.debugPanel.get())
        {
            this.panels.registerPanel(new GuiDebugPanel(mc, this), IKey.str("Debug"), Icons.POSE);
        }

        this.panels.setPanel(this.config);
    }
}
