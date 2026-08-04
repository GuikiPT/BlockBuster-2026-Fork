package mchorse.blockbuster.network;

import mchorse.mclib.network.AbstractDispatcher;
import mchorse.mclib.network.mclib.client.AbstractClientHandlerAnswer;
import mchorse.mclib.utils.NextTickQueue;
import net.minecraft.network.PacketByteBuf;

/**
 * Client-side network presence state (roadmap P27).
 *
 * <p>1.12.2 had no explicit handshake — presence rode Forge's mod-list
 * exchange, and McLib's {@code @NetworkCheckHandler} returned {@code true}
 * unconditionally (vanilla clients on Blockbuster servers and vice versa are
 * a compatibility promise). Fabric has no mod-list exchange, so the server
 * sends {@code blockbuster:handshake} on {@code ServerPlayConnectionEvents.JOIN}
 * and this class tracks the flag. Nothing is ever required of the remote.</p>
 *
 * <p>The flag defaults to {@code false} — including in single-player until the
 * integrated server's JOIN handshake arrives — so consumers (GUI panels,
 * {@code /record} client features, later stages) must degrade gracefully.</p>
 *
 * <p>{@link #resetHandshake()} is the ONE disconnect hook owning all client
 * network state: flag, P25 chunk reassembly, P26 pending answer callbacks and
 * the client next-tick queue (idea from BBS's {@code resetHandshake}, which
 * also reset its crusher).</p>
 */
public final class ClientNetworkState
{
    private static volatile boolean isBlockbusterOnServer;
    private static volatile String serverVersion = "";

    private ClientNetworkState()
    {}

    public static boolean isBlockbusterOnServer()
    {
        return isBlockbusterOnServer;
    }

    public static String getServerVersion()
    {
        return serverVersion;
    }

    /**
     * Handshake receiver body (netty thread; only touches volatile flags).
     * Total reader: the payload is a version string today, and any trailing
     * unknown bytes from future servers are ignored; a malformed/empty
     * payload still sets the presence flag.
     */
    public static void onHandshake(PacketByteBuf buf)
    {
        isBlockbusterOnServer = true;

        try
        {
            serverVersion = buf.readString();
        }
        catch (RuntimeException e)
        {
            serverVersion = "";
        }
    }

    public static void resetHandshake()
    {
        isBlockbusterOnServer = false;
        serverVersion = "";

        /* all client-side connection-scoped pools reset in one place */
        AbstractDispatcher.resetAllClientState();
        AbstractClientHandlerAnswer.resetAll();
        NextTickQueue.CLIENT.clear();
    }
}
