package mchorse.mclib.network.mclib.server;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.PacketRequestPermission;
import mchorse.mclib.permissions.PermissionCategory;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Full port of McLib 2.4.3's ServerHandlerPermissionRequest (roadmap P26).
 *
 * <p>Port note: legacy answered via the client-source
 * {@code ClientHandlerAnswer.sendAnswerTo(...)} static — which just forwarded
 * to the mclib Dispatcher and is client-source-set now, so this handler sends
 * through {@code Dispatcher.sendTo} directly (byte-identical behavior).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/server/ServerHandlerPermissionRequest.java</p>
 */
public class ServerHandlerPermissionRequest extends ServerMessageHandler<PacketRequestPermission>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestPermission message)
    {
        PermissionCategory perm = message.getPermissionRequest();

        boolean hasPermission = perm != null && perm.playerHasPermission(player);

        Dispatcher.sendTo(message.getAnswer(hasPermission), player);
    }
}
