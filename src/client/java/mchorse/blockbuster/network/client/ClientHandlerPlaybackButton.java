package mchorse.blockbuster.network.client;

import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.network.common.PacketPlaybackButton;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketPlaybackButton} (roadmap P129, live-wired in
 * S22/P228).
 *
 * <p>Legacy body, 1:1:</p>
 * <pre>
 * GuiPlayback playback = new GuiPlayback();
 * playback.setLocation(message.location, message.scenes);
 * Minecraft.getMinecraft().displayGuiScreen(playback);
 * </pre>
 *
 * <p>Those three lines are exactly {@link CameraHandler#attach} in the port —
 * the seam {@code CameraHandlerClient.register()} installs at client init
 * ({@code playbackScreenOpener}). Calling it here instead of re-building the
 * screen keeps one construction site for {@code GuiPlayback} and lets headless
 * tests capture the open by swapping that opener.</p>
 */
public class ClientHandlerPlaybackButton extends ClientMessageHandler<PacketPlaybackButton>
{
    @Override
    public void run(ClientPlayerEntity player, PacketPlaybackButton message)
    {
        CameraHandler.attach(message.location, message.scenes);
    }
}
