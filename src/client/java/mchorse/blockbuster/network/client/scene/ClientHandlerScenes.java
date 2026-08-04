package mchorse.blockbuster.network.client.scene;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.network.common.scene.PacketScenes;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketScenes} (roadmap P131). Appends the delivered
 * scene names into the scene panel's list (null-guarded, no dedupe — the GUI
 * clears before requesting, P133). 1:1 behavior port of 1.12.2
 * {@code ClientHandlerScenes.java} ({@code ClientProxy.panels} →
 * {@link BlockbusterClient#panels}).
 */
public class ClientHandlerScenes extends ClientMessageHandler<PacketScenes>
{
    @Override
    public void run(ClientPlayerEntity player, PacketScenes message)
    {
        GuiBlockbusterPanels dashboard = BlockbusterClient.panels;

        if (dashboard.scenePanel != null)
        {
            dashboard.scenePanel.scenes.add(message.scenes);
        }
    }
}
