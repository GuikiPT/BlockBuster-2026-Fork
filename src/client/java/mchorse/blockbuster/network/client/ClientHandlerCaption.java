package mchorse.blockbuster.network.client;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.network.common.PacketCaption;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler for {@link PacketCaption} (roadmap P116). A null/empty caption
 * hides the overlay; text is applied unformatted in plain (non-filename) mode.
 */
public class ClientHandlerCaption extends ClientMessageHandler<PacketCaption>
{
    @Override
    public void run(ClientPlayerEntity player, PacketCaption message)
    {
        String caption = message.caption == null ? "" : message.caption.getString();

        ClientProxy.recordingOverlay.setVisible(!caption.isEmpty());
        ClientProxy.recordingOverlay.setCaption(caption, false);
    }
}
