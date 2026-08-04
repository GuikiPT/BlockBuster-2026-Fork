package mchorse.metamorph.network.server.creative;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.network.common.creative.PacketAcquireMorph;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler acquire morph (roadmap P55).
 *
 * <p>Responsible for granting an acquired morph to players in the creative
 * morph menu. Gated by creative || spectator.</p>
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/server/creative/ServerHandlerAcquireMorph.java</p>
 */
public class ServerHandlerAcquireMorph extends ServerMessageHandler<PacketAcquireMorph>
{
    @Override
    public void run(ServerPlayerEntity player, PacketAcquireMorph message)
    {
        if (player.isCreative() || player.isSpectator())
        {
            MorphAPI.acquire(player, message.morph);
        }
    }
}
