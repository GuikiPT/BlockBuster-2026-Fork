package mchorse.mclib.network;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Full port of McLib 2.4.3's AbstractMessageHandler (roadmap P23).
 *
 * <p>Base of all message handlers. The Forge {@code IMessageHandler<T, IMessage>}
 * super-interface is gone; {@code onMessage(message, ctx)} side-dispatch is now
 * performed by {@link AbstractDispatcher}'s receive path, which calls
 * {@link #handleClientMessage} or {@link #handleServerMessage} directly and
 * sends any non-null returned reply back through the same dispatcher — the
 * legacy reply contract is preserved.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/AbstractMessageHandler.java</p>
 *
 * @author Ernio (Ernest Sadowski)
 */
public abstract class AbstractMessageHandler<T extends IMessage>
{
    /**
     * Handle a message received on the client side
     *
     * @return a message to send back to the Server, or null if no reply is
     *         necessary
     */
    public abstract IMessage handleClientMessage(final T message);

    /**
     * Handle a message received on the server side
     *
     * @return a message to send back to the Client, or null if no reply is
     *         necessary
     */
    public abstract IMessage handleServerMessage(final ServerPlayerEntity player, final T message);
}
