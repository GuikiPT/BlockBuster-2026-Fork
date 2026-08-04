package mchorse.blockbuster.network.server.recording.actions;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.actions.PacketActionList;
import mchorse.blockbuster.network.common.recording.actions.PacketRequestActions;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * OP-gated reply with the server's record listing (roadmap P117).
 */
public class ServerHandlerRequestActions extends ServerMessageHandler<PacketRequestActions>
{
    @Override
    public void run(ServerPlayerEntity player, PacketRequestActions message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Dispatcher.sendTo(new PacketActionList(RecordUtils.getReplays()), player);
    }
}
