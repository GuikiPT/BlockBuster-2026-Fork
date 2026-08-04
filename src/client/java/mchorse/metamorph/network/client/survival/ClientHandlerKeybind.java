package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketKeybind;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler applying a server-confirmed keybind change (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerKeybind.java</p>
 */
public class ClientHandlerKeybind extends ClientMessageHandler<PacketKeybind>
{
    @Override
    public void run(ClientPlayerEntity player, PacketKeybind message)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing != null)
        {
            morphing.keybind(message.index, message.keybind);
        }
    }
}
