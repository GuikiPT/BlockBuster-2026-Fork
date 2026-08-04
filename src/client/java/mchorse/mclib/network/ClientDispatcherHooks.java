package mchorse.mclib.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

/**
 * Client-only dispatcher wiring (roadmap P23). {@code ClientPlayNetworking}
 * cannot be classloaded on a dedicated server, so all client-side channel
 * registration and the client→server send path live here, in the client
 * source set, invoked from the {@code ClientModInitializer} (which runs after
 * common init — the dispatcher records CLIENT registrations during
 * {@code register()} and this hook wires them afterwards).
 */
public final class ClientDispatcherHooks
{
    private ClientDispatcherHooks()
    {}

    /** Installs the client→server sender seam ({@code AbstractDispatcher.sendToServer}). */
    public static void install()
    {
        AbstractDispatcher.setClientSender(ClientPlayNetworking::send);
    }

    /**
     * Registers Fabric client receivers for every CLIENT-side channel of the
     * given dispatcher, plus its chunk channel — all funneling into
     * {@code AbstractDispatcher.receiveClient} (decode on netty, handler hops
     * to the game thread).
     */
    public static void registerClientReceivers(AbstractDispatcher dispatcher)
    {
        for (Identifier channel : dispatcher.getClientChannels())
        {
            ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buf, responseSender) -> dispatcher.receiveClient(channel, buf));
        }

        Identifier chunk = dispatcher.getChunkChannel();

        ClientPlayNetworking.registerGlobalReceiver(chunk, (client, handler, buf, responseSender) -> dispatcher.receiveClient(chunk, buf));
    }
}
