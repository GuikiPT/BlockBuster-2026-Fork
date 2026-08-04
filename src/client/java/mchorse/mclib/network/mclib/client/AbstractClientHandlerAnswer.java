package mchorse.mclib.network.mclib.client;

import mchorse.mclib.network.AbstractDispatcher;
import mchorse.mclib.network.ClientMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import mchorse.mclib.network.mclib.common.IAnswerRequest;
import mchorse.mclib.network.mclib.common.PacketAnswer;
import mchorse.mclib.utils.Consumers;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Full port of McLib 2.4.3's AbstractClientHandlerAnswer (roadmap P26/P28) —
 * the generic client-side answer framework behind {@code IAnswerRequest}.
 *
 * <p>The legacy {@code @SubscribeEvent onClientTick} END-phase sweep is
 * rehosted on Fabric's {@code ClientTickEvents.END_CLIENT_TICK} (wired in
 * {@code BlockbusterClient}) as {@link #sweep(long)}. Parity note: legacy
 * removed from {@code TIME} <b>while iterating its entrySet</b> — a
 * ConcurrentModificationException on every actual timeout (it would have
 * crashed the client; not load-bearing). The port iterates with an explicit
 * iterator + {@code it.remove()} — behavior-identical for the non-timeout
 * path, actually working for the timeout path.</p>
 *
 * <p>{@code requestServerAnswer} works with any {@link AbstractDispatcher},
 * so Blockbuster-channel requests ({@code PacketFramesOverwrite}, P115)
 * answer over this mclib machinery. All static state is touched from the
 * client game thread only (post-hop, P28); {@link #resetAll()} is P27's
 * disconnect cleanup.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/client/AbstractClientHandlerAnswer.java</p>
 */
public abstract class AbstractClientHandlerAnswer<T extends PacketAnswer> extends ClientMessageHandler<T>
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** 5 minute timeout (legacy constant, inlined there too) */
    public static final long ANSWER_TIMEOUT_MS = 5 * 60000L;

    protected static final Consumers<Object> CONSUMERS = new Consumers<>();
    protected static final Map<Integer, Long> TIME = new HashMap<>();

    /**
     * For logging / debugging purposes
     */
    protected static final Map<Integer, IAnswerRequest<?>> REQUESTS = new HashMap<>();

    @Override
    public void run(ClientPlayerEntity player, PacketAnswer message)
    {
        CONSUMERS.consume(message.getCallbackID(), message.getValue());
        TIME.remove(message.getCallbackID());
        REQUESTS.remove(message.getCallbackID());
    }

    /**
     * Timeout sweep, invoked every END_CLIENT_TICK with the current wall
     * clock (parameterized for headless tests).
     */
    public static void sweep(long now)
    {
        Iterator<Map.Entry<Integer, Long>> it = TIME.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Integer, Long> entry = it.next();

            if (entry.getValue() + ANSWER_TIMEOUT_MS < now)
            {
                IAnswerRequest<?> request = REQUESTS.get(entry.getKey());

                LOGGER.info("Timeout for the answer request " + (request == null ? "<unknown>" : request.getClass().getSimpleName()) + ". The consumer has been removed.");

                CONSUMERS.remove(entry.getKey());
                REQUESTS.remove(entry.getKey());
                it.remove();
            }
        }
    }

    /**
     * P27 disconnect cleanup: stale callbacks must not survive across
     * servers. Every live consumer id has a TIME stamp (they are only
     * registered through {@link #requestServerAnswer}), so clearing via the
     * TIME keys empties all three pools.
     */
    public static void resetAll()
    {
        for (Integer id : TIME.keySet())
        {
            CONSUMERS.remove(id);
        }

        TIME.clear();
        REQUESTS.clear();
    }

    /**
     * This will register the consumer and set the resulting callbackID to the provided AnswerRequest.
     * The AnswerRequest will then be sent to the server.
     * @param request
     * @param callback
     */
    public static <T extends Serializable> void requestServerAnswer(AbstractDispatcher dispatcher, IAnswerRequest<T> request, Consumer<T> callback)
    {
        int id = CONSUMERS.register((obj) ->
        {
            T param;

            try
            {
                param = (T) obj;
            }
            catch (ClassCastException e)
            {
                LOGGER.error("Type of the answer's value is incompatible with the consumer generic type!");
                e.printStackTrace();

                return;
            }

            callback.accept(param);
        });

        TIME.put(id, System.currentTimeMillis());
        REQUESTS.put(id, request);
        request.setCallbackID(id);

        dispatcher.sendToServer(request);
    }

    /**
     * Send the answer to the player. The answer's generic datatype needs to be equal
     * to the Consumer input datatype that has been registered on the client side.
     * @param receiver
     * @param answer
     * @param <T> the type of the registered Consumer input datatype.
     */
    public static <T extends Serializable> void sendAnswerTo(ServerPlayerEntity receiver, PacketAnswer<T> answer)
    {
        Dispatcher.sendTo(answer, receiver);
    }
}
