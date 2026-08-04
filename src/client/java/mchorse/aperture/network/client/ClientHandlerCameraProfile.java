package mchorse.aperture.network.client;

import mchorse.aperture.Aperture;
import mchorse.aperture.ClientProxy;
import mchorse.aperture.camera.CameraControl;
import mchorse.aperture.camera.CameraProfile;
import mchorse.aperture.camera.destination.ServerDestination;
import mchorse.aperture.network.common.PacketCameraProfile;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.GameMode;

/**
 * Load a server-sent camera profile into the editor (P182). The profile gets a
 * {@link ServerDestination}, is marked clean, and merged into the profiles
 * manager; if the server asked, playback starts.
 *
 * <p>Port note: the {@code "profile.load"} success chat is suppressed for
 * adventure-mode players (hides spam from Blockbuster scene playback) — the
 * adventure check uses the client gamemode ({@code CameraControl.getGameMode()})
 * in place of legacy {@code EntityUtils.isAdventureMode}.</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/client/ClientHandlerCameraProfile.java</p>
 */
public class ClientHandlerCameraProfile extends ClientMessageHandler<PacketCameraProfile>
{
    @Override
    public void run(ClientPlayerEntity player, PacketCameraProfile message)
    {
        CameraProfile profile = message.profile;

        profile.setDestination(new ServerDestination(message.filename));
        profile.dirty = false;

        ClientProxy.getCameraEditor().profiles.addProfile(profile);

        if (message.play)
        {
            ClientProxy.runner.start(ClientProxy.control.currentProfile);
        }

        if (CameraControl.getGameMode() != GameMode.ADVENTURE)
        {
            Aperture.l10n.success(player, "profile.load", message.filename);
        }
    }
}
