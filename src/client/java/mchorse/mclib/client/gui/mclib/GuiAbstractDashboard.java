package mchorse.mclib.client.gui.mclib;

import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Keybind;
import mchorse.mclib.events.RegisterDashboardPanels;
import mchorse.mclib.permissions.PermissionUtils;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiAbstractDashboard} (roadmap P44).
 *
 * <p>1.20.4 boundary mapping:
 * {@code setWorldAndResolution} → {@code init(MinecraftClient, int, int)}
 * (called on display AND resize, like legacy), {@code onGuiClosed} →
 * {@code removed()}, {@code doesGuiPauseGame} → {@code shouldPause()}
 * (already false on {@code GuiBase}). The background pass binds the frame's
 * {@code DrawContext} into {@code GuiDraw} before drawing because it runs
 * before {@code super.render(...)} does its own bind.</p>
 *
 * <p>{@code RegisterDashboardPanels} is posted through the P22-style
 * callback list (see that class) after the built-in panels register —
 * Blockbuster (S12) and Aperture (S15) hook there.</p>
 */
public abstract class GuiAbstractDashboard extends GuiBase
{
    public GuiDashboardPanels panels;
    public GuiDashboardPanel defaultPanel;

    private boolean wasClosed = true;
    private int opLevel = -1;

    public GuiAbstractDashboard(MinecraftClient mc)
    {
        this.panels = this.createDashboardPanels(mc);

        this.panels.flex().relative(this.viewport).wh(1F, 1F);
        this.registerPanels(mc);

        RegisterDashboardPanels.post(new RegisterDashboardPanels(this));

        this.root.add(this.panels);
    }

    protected abstract GuiDashboardPanels createDashboardPanels(MinecraftClient mc);

    protected abstract void registerPanels(MinecraftClient mc);

    /**
     * Legacy {@code onGuiClosed()}
     */
    @Override
    public void removed()
    {
        this.closeDashboard();
        super.removed();
    }

    /**
     * Legacy private {@code close()} — renamed because 1.20.4's
     * {@code Screen.close()} is a public vanilla method (ESC handling).
     */
    private void closeDashboard()
    {
        this.panels.close();
        this.wasClosed = true;
    }

    /**
     * Legacy {@code setWorldAndResolution(Minecraft, int, int)} — runs both
     * when the screen is displayed and on every resize.
     * ({@code Screen.init(MinecraftClient, int, int)} is final on 1.20.4;
     * it delegates to this no-arg {@code init()}, so the legacy
     * "checks before super" ordering is preserved here.)
     */
    @Override
    protected void init()
    {
        this.checkPermissions();

        if (this.wasClosed)
        {
            this.wasClosed = false;
            this.panels.open();
            this.panels.setPanel(this.panels.view.delegate);
        }

        super.init();
    }

    private void checkPermissions()
    {
        int newOpLevel = OpHelper.getPlayerOpLevel();

        for (GuiDashboardPanel panel : this.panels.panels)
        {
            GuiIconElement button = this.panels.getButton(panel);

            Consumer<Boolean> task = (enabled) ->
            {
                button.setEnabled(enabled);

                for (Keybind keybind : this.panels.keys().keybinds)
                {
                    keybind.active(enabled);
                }
            };

            if (panel.getRequiredPermission() != null)
            {
                PermissionUtils.hasPermission(this.context.mc == null ? null : this.context.mc.player, panel.getRequiredPermission(), task);
            }
            else
            {
                task.accept(true);
            }
        }

        GuiDashboardPanel current = this.panels.view.delegate;

        if (current != null && current.getRequiredPermission() != null)
        {
            this.panels.setPanel(null);

            PermissionUtils.hasPermission(this.context.mc == null ? null : this.context.mc.player, current.getRequiredPermission(), (allowed) ->
            {
                if (allowed)
                {
                    this.panels.setPanel(current);
                }
                else
                {
                    this.panels.setPanel(this.defaultPanel);
                }
            });
        }
        else if (current == null)
        {
            this.panels.setPanel(null);
        }

        this.opLevel = newOpLevel;
    }

    /**
     * Legacy {@code drawScreen(int, int, float)} — custom background when
     * the active panel wants one, top/bottom gradients otherwise.
     */
    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float partialTicks)
    {
        /* super.render binds the DrawContext only after this pass, bind
         * early so the background helpers hit the right frame */
        GuiDraw.bindDrawContext(drawContext);

        if (this.panels.view.delegate != null && this.panels.view.delegate.needsBackground())
        {
            GuiDraw.drawCustomBackground(0, 0, this.width, this.height);
        }
        else
        {
            GuiDraw.drawVerticalGradientRect(0, 0, this.width, this.height / 8, 0x44000000, 0);
            GuiDraw.drawVerticalGradientRect(0, this.height - this.height / 8, this.width, this.height, 0, 0x44000000);
        }

        super.render(drawContext, mouseX, mouseY, partialTicks);
    }
}
