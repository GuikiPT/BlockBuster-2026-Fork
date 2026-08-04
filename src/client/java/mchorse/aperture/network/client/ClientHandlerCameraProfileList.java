package mchorse.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.camera.destination.ClientDestination;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.client.gui.GuiProfilesManager;
import mchorse.aperture.network.common.PacketCameraProfileList;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Merge the server's profile-name list into the open camera editor (P182).
 * No-ops safely when the editor screen isn't open. Client profiles are listed
 * alongside the server ones, the list re-filters/sorts, and the first entry is
 * selected when nothing is selected yet.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerCameraProfileList.java</p>
 */
public class ClientHandlerCameraProfileList extends ClientMessageHandler<PacketCameraProfileList>
{
    @Override
    public void run(ClientPlayerEntity player, PacketCameraProfileList message)
    {
        Screen current = MinecraftClient.getInstance().currentScreen;

        if (current instanceof GuiCameraEditor)
        {
            GuiProfilesManager manager = ((GuiCameraEditor) current).profiles;

            for (String filename : CameraAPI.getClientProfiles())
            {
                manager.addProfile(new ClientDestination(filename));
            }

            for (String profile : message.cameras)
            {
                manager.addProfile(new ServerDestination(profile));
            }

            manager.profiles.filter("", true);
            manager.profiles.list.sort();

            if (ClientProxy.control.currentProfile == null)
            {
                manager.selectFirstAvailable(manager.profiles.list.getIndex());
            }
        }
    }
}
