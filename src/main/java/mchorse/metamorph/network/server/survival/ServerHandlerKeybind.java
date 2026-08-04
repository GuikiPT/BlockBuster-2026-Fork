package mchorse.metamorph.network.server.survival;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.Dispatcher;
import mchorse.metamorph.network.common.survival.PacketKeybind;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler binding a morph to a keybind slot (roadmap P55). Echoes the
 * packet back so the client GUI keybind state is server-confirmed.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/survival/ServerHandlerKeybind.java</p>
 */
public class ServerHandlerKeybind extends ServerMessageHandler<PacketKeybind>
{
    @Override
    public void run(ServerPlayerEntity player, PacketKeybind message)
    {
        Morphing.get(player).keybind(message.index, message.keybind);
        Dispatcher.sendTo(message, player);
    }
}
