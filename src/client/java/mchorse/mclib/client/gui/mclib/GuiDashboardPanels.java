package mchorse.mclib.client.gui.mclib;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.GuiPanelBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.permissions.PermissionUtils;
import mchorse.mclib.utils.Direction;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiDashboardPanels} (roadmap P44) — the
 * left-side icon strip + panel switcher of the dashboard. Registration
 * order defines the tab strip order (a P37 {@code GuiPanelBase} contract
 * Blockbuster/Aperture panels rely on); the first ten panels get
 * numpad 0–9 open keybinds; switching fires the legacy
 * {@code disappear}/{@code appear} lifecycle pair.
 */
public class GuiDashboardPanels extends GuiPanelBase<GuiDashboardPanel>
{
    public GuiDashboardPanels(MinecraftClient mc)
    {
        super(mc, Direction.LEFT);
    }

    public void open()
    {
        for (GuiDashboardPanel panel : this.panels)
        {
            Consumer<Boolean> task = (enabled) ->
            {
                if (enabled)
                {
                    panel.open();
                }
            };

            if (panel.getRequiredPermission() != null)
            {
                PermissionUtils.hasPermission(MinecraftClient.getInstance() == null ? null : MinecraftClient.getInstance().player, panel.getRequiredPermission(), task);
            }
            else
            {
                task.accept(true);
            }
        }
    }

    public void close()
    {
        for (GuiDashboardPanel panel : this.panels)
        {
            panel.close();
        }
    }

    @Override
    public void setPanel(GuiDashboardPanel panel)
    {
        if (this.view.delegate != null)
        {
            this.view.delegate.disappear();
        }

        super.setPanel(panel);

        if (this.view.delegate != null)
        {
            this.view.delegate.appear();
        }
    }

    @Override
    public GuiIconElement registerPanel(GuiDashboardPanel panel, IKey tooltip, Icon icon)
    {
        GuiIconElement element = super.registerPanel(panel, tooltip, icon);

        int key = this.getKeybind();

        if (key != -1)
        {
            element.keys()
                .register(IKey.comp(IKey.lang("mclib.gui.dashboard.open_panel"), tooltip), key, () -> element.clickItself(GuiBase.getCurrent()))
                .category(IKey.lang("mclib.gui.dashboard.category"));
        }

        return element;
    }

    protected int getKeybind()
    {
        int size = this.panels.size();

        switch (size)
        {
            case 1: return LegacyKeyCodes.KEY_NUMPAD0;
            case 2: return LegacyKeyCodes.KEY_NUMPAD1;
            case 3: return LegacyKeyCodes.KEY_NUMPAD2;
            case 4: return LegacyKeyCodes.KEY_NUMPAD3;
            case 5: return LegacyKeyCodes.KEY_NUMPAD4;
            case 6: return LegacyKeyCodes.KEY_NUMPAD5;
            case 7: return LegacyKeyCodes.KEY_NUMPAD6;
            case 8: return LegacyKeyCodes.KEY_NUMPAD7;
            case 9: return LegacyKeyCodes.KEY_NUMPAD8;
            case 10: return LegacyKeyCodes.KEY_NUMPAD9;
        }

        return -1;
    }

    @Override
    protected void drawBackground(GuiContext context, int x, int y, int w, int h)
    {
        GuiDraw.drawRect(x, y, x + w, y + h, 0xff111111);
    }
}
