package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.scene.PacketSceneRecord;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketSceneRecord} (roadmap P131). OP-gated. When
 * the location is a scene it records into that scene's slot; otherwise it falls
 * back to a standalone {@code Mode.ACTIONS} recording (teleport-back + notify
 * both true). 1:1 behavior port of 1.12.2 {@code ServerHandlerSceneRecord.java}.
 */
public class ServerHandlerSceneRecord extends ServerMessageHandler<PacketSceneRecord>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSceneRecord message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (message.location.isScene())
        {
            CommonProxy.scenes.record(message.location.getFilename(), message.record, message.offset, player);
        }
        else
        {
            CommonProxy.manager.record(message.record, player, Mode.ACTIONS, true, true, message.offset, null);
        }
    }
}
