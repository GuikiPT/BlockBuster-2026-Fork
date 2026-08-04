package mchorse.mclib.client.gui.mclib;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.permissions.PermissionCategory;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiDashboardPanel} (roadmap P44) — base class
 * of every dashboard panel. Panels are containers; permission gating goes
 * through {@link #getRequiredPermission()} (S1 P21 categories), the
 * deprecated op-level gate is kept for legacy call sites.
 */
public class GuiDashboardPanel<T extends GuiAbstractDashboard> extends GuiElement
{
    public final T dashboard;

    public GuiDashboardPanel(MinecraftClient mc, T dashboard)
    {
        super(mc);

        this.dashboard = dashboard;
        this.markContainer();
    }

    @Deprecated
    public boolean canBeOpened(int opLevel)
    {
        return this.isClientSideOnly() || OpHelper.isOp(opLevel);
    }

    public PermissionCategory getRequiredPermission()
    {
        return null;
    }

    public boolean isClientSideOnly()
    {
        return false;
    }

    public boolean needsBackground()
    {
        return true;
    }

    public void appear()
    {}

    public void disappear()
    {}

    public void open()
    {}

    public void close()
    {}
}
