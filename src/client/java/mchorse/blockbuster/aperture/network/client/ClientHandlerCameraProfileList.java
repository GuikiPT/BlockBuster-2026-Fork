package mchorse.blockbuster.aperture.network.client;

import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.network.common.PacketCameraProfileList;
import mchorse.blockbuster.aperture.gui.GuiPlayback;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client receiver for the server profile list, <b>on Blockbuster's channel</b>
 * (roadmap P185.1). 1:1 port of 1.12.2
 * {@code aperture/network/client/ClientHandlerCameraProfileList.java}.
 *
 * <p>Aperture registers its own handler for the same packet class on its own
 * channel (that one feeds {@code GuiProfilesManager}); this one only fills the
 * playback button screen, and silently drops the reply when that screen is not
 * the current one — the request is fire-and-forget, so a player who closed the
 * screen before the round trip finished just gets nothing.</p>
 */
public class ClientHandlerCameraProfileList extends ClientMessageHandler<PacketCameraProfileList>
{
    @Override
    public void run(ClientPlayerEntity player, PacketCameraProfileList message)
    {
        Screen current = MinecraftClient.getInstance().currentScreen;

        if (current instanceof GuiPlayback)
        {
            GuiPlayback gui = (GuiPlayback) current;

            for (String filename : message.cameras)
            {
                gui.addDestination(new ServerDestination(filename));
            }

            gui.profiles.sort();
            gui.selectCurrent();
        }
    }
}
