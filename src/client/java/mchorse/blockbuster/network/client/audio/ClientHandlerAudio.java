package mchorse.blockbuster.network.client.audio;

import mchorse.blockbuster.ClientProxy;
import mchorse.blockbuster.client.video.SceneAudioTracker;
import mchorse.blockbuster.network.common.audio.PacketAudio;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client receiver for {@link PacketAudio} (roadmap S16 P189). One-liner port of
 * 2.7.2 {@code network/client/audio/ClientHandlerAudio.java}: hand the command
 * off to the client-side {@code AudioLibrary} state machine (P188).
 *
 * <p>Lives in the split client source set (replaces the legacy
 * {@code @SideOnly(Side.CLIENT)}). The {@link ClientMessageHandler} base hops
 * {@code run} to the client game thread (P28).</p>
 */
public class ClientHandlerAudio extends ClientMessageHandler<PacketAudio>
{
    @Override
    public void run(ClientPlayerEntity player, PacketAudio message)
    {
        /* S22 P234: remember the scene's track so the built-in video recorder can
         * mux it (MinemaBackend.audioResolver). This packet is the only place the
         * client is told which .wav a scene plays. */
        SceneAudioTracker.accept(message.audio, message.shift);

        ClientProxy.audio.handleAudio(message.audio, message.state, message.shift, message.delay);
    }
}
