package mchorse.metamorph.network.client.survival;

import mchorse.mclib.network.ClientMessageHandler;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import mchorse.metamorph.network.common.survival.PacketFavorite;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client handler applying a server-confirmed favorite toggle (roadmap P55).
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/client/survival/ClientHandlerFavorite.java</p>
 */
public class ClientHandlerFavorite extends ClientMessageHandler<PacketFavorite>
{
    @Override
    public void run(ClientPlayerEntity player, PacketFavorite message)
    {
        IMorphing morphing = Morphing.get(player);

        if (morphing != null)
        {
            morphing.favorite(message.index);
        }
    }
}
