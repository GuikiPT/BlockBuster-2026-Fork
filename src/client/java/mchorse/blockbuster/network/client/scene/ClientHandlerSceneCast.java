package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.client.gui.dashboard.panels.scene.GuiScenePanel;
import mchorse.blockbuster.network.common.scene.PacketSceneCast;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketSceneCast} (roadmap P131). Transfers a
 * requested scene cast to the director panel.
 *
 * <p>Three branches, exactly like 1.12.2 {@code ClientHandlerSceneCast.java}:
 * <ul>
 *   <li>{@code opened = open && no screen} → {@link GuiScenePanel#openScene}
 *       and bring the dashboard forward;</li>
 *   <li>else if the current screen <em>is</em> the dashboard →
 *       {@link GuiScenePanel#setScene} (swap active scene in place);</li>
 *   <li>else → {@link GuiScenePanel#set} — the silent state update used by the
 *       login-sync {@code open(false)} path (P131.1).</li>
 * </ul></p>
 *
 * <p>The foreground switch mirrors legacy exactly: activate the scene panel on
 * the dashboard's panel strip ({@code dashboard.panels.setPanel}) and only then
 * display the dashboard screen.</p>
 */
public class ClientHandlerSceneCast extends ClientMessageHandler<PacketSceneCast>
{
    @Override
    public void run(ClientPlayerEntity player, PacketSceneCast message)
    {
        GuiBlockbusterPanels panels = BlockbusterClient.panels;

        if (panels.scenePanel == null)
        {
            return;
        }

        GuiDashboard dashboard = GuiDashboard.get();
        Screen screen = this.getCurrentScreen();

        boolean opened = message.open && screen == null;

        if (opened)
        {
            panels.scenePanel.openScene(message.location);
        }
        else if (screen == dashboard)
        {
            panels.scenePanel.setScene(message.location);
        }
        else
        {
            panels.scenePanel.set(message.location);
        }

        if (opened && dashboard != null)
        {
            /* P133: the scene panel is a real dashboard panel (registered by
             * GuiBlockbusterPanels.onRegister on the RegisterDashboardPanels
             * event), so activate it before the screen is shown — legacy order
             * exactly: setPanel first, displayGuiScreen second. */
            dashboard.panels.setPanel(panels.scenePanel);

            this.displayScreen(dashboard);
        }
    }

    /** {@code Minecraft.getMinecraft().currentScreen} — seam for headless runs. */
    protected Screen getCurrentScreen()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        return mc == null ? null : mc.currentScreen;
    }

    /** {@code Minecraft.getMinecraft().displayGuiScreen(screen)} — seam for headless runs. */
    protected void displayScreen(Screen screen)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null)
        {
            mc.setScreen(screen);
        }
    }
}
