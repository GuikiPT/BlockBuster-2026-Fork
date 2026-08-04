package mchorse.metamorph.network.client;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.network.common.PacketBlacklist;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler installing the server's morph blacklist (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/ClientHandlerBlacklist.java</p>
 */
public class ClientHandlerBlacklist extends ClientMessageHandler<PacketBlacklist>
{
    @Override
    public void run(ClientPlayerEntity player, PacketBlacklist message)
    {
        MorphManager.INSTANCE.setActiveBlacklist(player.getWorld(), message.blacklist);
    }
}
