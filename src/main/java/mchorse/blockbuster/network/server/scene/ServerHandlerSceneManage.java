package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.commands.CommandRecord;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneManage;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketSceneManage} (roadmap P131). OP-gated.
 *
 * <p>RENAME / REMOVE apply the disk operation and echo the packet back to the
 * sender <b>only when it returned true</b>; DUPE echoes <b>unconditionally</b>
 * at the end (even if every record copy was skipped or threw). DUPE copies only
 * the <b>record</b> files — the duplicated scene file itself is saved by the
 * client's follow-up {@code PacketSceneCast} (P133), not here (division of
 * labor preserved from legacy). Per-replay exceptions are swallowed with a
 * stack trace; the {@code while} counter loop stays commented out (no
 * {@code _1_2_3} chains — {@code counter} is always 0), and a destination whose
 * record file already exists is skipped silently.</p>
 *
 * <p><b>Port parity note:</b> 1.12.2's class carried unused
 * {@code net.minecraft.client.Minecraft} + client-GUI imports — a
 * dedicated-server classloading hazard. This port keeps the handler strictly
 * common/server (no client references), as the plan mandates.</p>
 *
 * <p>The echo of {@link PacketSceneManage} is dropped on the client — the
 * legacy client handler is a no-op registered mis-sided (see the P131 parity
 * note in {@code ChannelLedger}); the GUI relies on optimistic local
 * updates.</p>
 */
public class ServerHandlerSceneManage extends ServerMessageHandler<PacketSceneManage>
{
    @Override
    public void run(ServerPlayerEntity player, PacketSceneManage message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (message.action == PacketSceneManage.RENAME && CommonProxy.scenes.rename(message.source, message.destination))
        {
            Dispatcher.sendTo(message, player);
        }
        else if (message.action == PacketSceneManage.REMOVE && CommonProxy.scenes.remove(message.source))
        {
            Dispatcher.sendTo(message, player);
        }
        else if (message.action == PacketSceneManage.DUPE)
        {
            Scene source = CommonProxy.scenes.get(message.source, player.getWorld());

            Scene destinationDummy = new Scene();

            destinationDummy.copy(source);
            destinationDummy.setId(message.destination);
            destinationDummy.setupIds();
            destinationDummy.renamePrefix(source.getId(), destinationDummy.getId(), (id) -> id + "_copy");

            for (int i = 0; i < destinationDummy.replays.size(); i++)
            {
                Replay replaySource = source.replays.get(i);
                Replay replayDestination = destinationDummy.replays.get(i);

                int counter = 0;

                try
                {
                    Record record = CommandRecord.getRecord(replaySource.id).clone();

                    if (RecordUtils.isReplayExists(replayDestination.id))
                    {
                        continue;
                    }

                    /* This could potentially cause renaming problems like _1_2_3_4 indexes
                    while (RecordUtils.isReplayExists(replayDestination.id + ((counter != 0) ? "_" + Integer.toString(counter) : "")))
                    {
                        counter++;
                    }*/

                    record.filename = replayDestination.id + ((counter != 0) ? "_" + Integer.toString(counter) : "");
                    replayDestination.id = record.filename;

                    record.save(RecordUtils.replayFile(record.filename));

                    CommonProxy.manager.records.put(record.filename, record);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            Dispatcher.sendTo(message, player);
        }
    }
}
