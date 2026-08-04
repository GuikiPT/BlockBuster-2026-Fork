package mchorse.blockbuster.network.server.scene.sync;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.network.common.scene.sync.PacketScenePlay;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketScenePlay} (roadmap P131). OP-gated.
 *
 * <p>PLAY on a non-playing scene calls <b>both</b> {@code spawn(tick)} (which
 * leaves it paused) and {@code resume(tick)} (which unpauses); a playing scene
 * just gets {@code resume(tick)}. STOP → {@code stopPlayback(true)}; PAUSE →
 * {@code pause()}; START → {@code spawn(tick)}; RESTART → {@code reload(tick)}.
 * 1:1 behavior port of 1.12.2 {@code sync/ServerHandlerScenePlay.java}.</p>
 *
 * <p><b>Total-reader deviation:</b> a null scene (bogus filename) logs a
 * warning and returns rather than NPEing as 1.12.2 did.</p>
 */
public class ServerHandlerScenePlay extends ServerMessageHandler<PacketScenePlay>
{
    @Override
    public void run(ServerPlayerEntity player, PacketScenePlay message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        Scene scene = message.get(player.getWorld());

        if (scene == null)
        {
            Blockbuster.LOGGER.warn("PacketScenePlay for missing scene '{}' — ignoring", message.location.getFilename());

            return;
        }

        if (message.isPlay())
        {
            if (!scene.playing)
            {
                scene.spawn(message.tick);
            }

            scene.resume(message.tick);
        }
        else if (message.isStop())
        {
            scene.stopPlayback(true);
        }
        else if (message.isPause())
        {
            scene.pause();
        }
        else if (message.isStart())
        {
            scene.spawn(message.tick);
        }
        else if (message.isRestart())
        {
            scene.reload(message.tick);
        }
    }
}
