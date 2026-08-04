package mchorse.blockbuster.client.gui.dashboard;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.mclib.GuiDashboardPanel;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * Port of Blockbuster 2.7.2's {@code GuiBlockbusterPanel} (roadmap P135) —
 * the trivial base class of every Blockbuster dashboard panel.
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/GuiBlockbusterPanel.java</p>
 *
 * <p><b>P135 STUB PHASE:</b> the six concrete panels are still placeholders
 * ({@link mchorse.blockbuster.client.gui.dashboard.panels}); real content
 * arrives in later phases. {@link #drawStubPlaceholder} is stub-only
 * scaffolding — it is removed when the real panel content lands and each
 * concrete panel stops overriding {@code draw} to call it.</p>
 */
public class GuiBlockbusterPanel extends GuiDashboardPanel<GuiDashboard>
{
    public GuiBlockbusterPanel(MinecraftClient mc, GuiDashboard dashboard)
    {
        super(mc, dashboard);
    }

    @Override
    public void appear()
    {
        /* Legacy appear() pops the one-time first-time welcome modal on every
         * Blockbuster panel (P142). addOverlay is a no-op once the modal has
         * been dismissed (config gate) or is already present (dedupe scan). */
        GuiFirstTime.addOverlay(this.dashboard);
    }

    /**
     * STUB-PHASE ONLY (P135): draw a centered two-line placeholder (panel
     * title + "arrives in ..." hint). Not legacy behavior — deleted once the
     * real panel content lands in each panel's own phase.
     */
    protected void drawStubPlaceholder(GuiContext context, IKey title, String phase)
    {
        int cx = this.area.mx();
        int cy = this.area.my();

        GuiDraw.drawCenteredString(this.font, title.get(), cx, cy - 8, 0xffffffff);
        GuiDraw.drawCenteredString(this.font, phase, cx, cy + 4, 0xffaaaaaa);

        super.draw(context);
    }
}
