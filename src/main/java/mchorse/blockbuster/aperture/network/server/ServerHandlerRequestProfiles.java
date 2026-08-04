package mchorse.blockbuster.aperture.network.server;

import mchorse.aperture.camera.CameraAPI;
import mchorse.aperture.network.common.PacketCameraProfileList;
import mchorse.blockbuster.aperture.network.common.PacketRequestProfiles;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server receiver for {@link PacketRequestProfiles} (roadmap P185.1). 1:1 port
 * of 1.12.2 {@code aperture/network/server/ServerHandlerRequestProfiles.java}.
 *
 * <p>Note the reply rides <b>Blockbuster's</b> channel, not Aperture's: the
 * same {@link PacketCameraProfileList} class is registered on both dispatchers
 * with a different client handler each ({@code GuiProfilesManager} on
 * Aperture's, the playback-button screen on Blockbuster's), which is exactly
 * how 1.12.2 kept the two flows apart.</p>
 */
public class ServerHandlerRequestProfiles extends ServerMessageHandler<PacketRequestProfiles>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestProfiles message)
    {
        Dispatcher.sendTo(new PacketCameraProfileList(CameraAPI.getServerProfiles()), player);
    }
}
