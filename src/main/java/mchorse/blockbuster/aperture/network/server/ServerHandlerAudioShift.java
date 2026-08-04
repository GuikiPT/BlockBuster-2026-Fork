package mchorse.blockbuster.aperture.network.server;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.aperture.network.common.PacketAudioShift;
import mchorse.blockbuster.recording.scene.Scene;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server receiver for {@link PacketAudioShift} (roadmap S16 P189). 1:1 port of
 * 2.7.2 {@code aperture/network/server/ServerHandlerAudioShift.java}: apply the
 * new audio shift to the addressed scene and persist it.
 *
 * <p>Load-bearing legacy quirks preserved verbatim: <b>no permission check</b>
 * on the sender, and the scene save's exception is <b>silently swallowed</b>
 * (empty catch). {@code Scene.setAudioShift} additionally re-seeks a live server
 * scene ({@code audioHandler.goTo(tick)}), so the edit is audible immediately
 * during playback.</p>
 */
public class ServerHandlerAudioShift extends ServerMessageHandler<PacketAudioShift>
{
    @Override
    public void run(ServerPlayerEntity player, PacketAudioShift message)
    {
        /* S19 P209: permission hook point */
        Scene scene = message.get(player.getWorld());

        if (scene != null)
        {
            scene.setAudioShift(message.shift);

            try
            {
                CommonProxy.scenes.save(scene.getId(), scene, false);
            }
            catch (Exception e)
            {}
        }
    }
}
