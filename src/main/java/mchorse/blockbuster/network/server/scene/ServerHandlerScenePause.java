package mchorse.blockbuster.network.server.scene;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.scene.PacketScenePause;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server handler for {@link PacketScenePause} (roadmap P131). OP-gated.
 *
 * <p><b>Dual purpose:</b> if the sender is currently recording, this cancels
 * the recording (with the {@code action.cancel} l10n info message) instead of
 * touching a scene. Otherwise it toggles the scene: {@code !isPlaying()} →
 * {@code resume(-1)}, else {@code pause()}. Note {@code isPlaying()} checks the
 * <em>actors'</em> playing flags (not {@code scene.playing}), so a paused scene
 * reports false and thus resumes — subtle, ported exactly.</p>
 *
 * <p><b>Total-reader deviation:</b> 1.12.2 called {@code message.get(world)}
 * with no null check (a bogus filename NPEs). Per the ground rule, a null scene
 * here logs a warning and returns instead of crashing.</p>
 */
public class ServerHandlerScenePause extends ServerMessageHandler<PacketScenePause>
{
    @Override
    public void run(ServerPlayerEntity player, PacketScenePause message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (CommonProxy.manager.recorders.containsKey(player))
        {
            if (CommonProxy.manager.cancel(player))
            {
                Blockbuster.l10n.info(player, "action.cancel");
            }
        }
        else
        {
            Scene scene = message.get(player.getWorld());

            if (scene == null)
            {
                Blockbuster.LOGGER.warn("PacketScenePause for missing scene '{}' — ignoring", message.location.getFilename());

                return;
            }

            if (!scene.isPlaying())
            {
                scene.resume(-1);
            }
            else
            {
                scene.pause();
            }
        }
    }
}
