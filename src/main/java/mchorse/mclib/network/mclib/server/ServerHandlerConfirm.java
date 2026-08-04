package mchorse.mclib.network.mclib.server;

import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.common.PacketConfirm;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Full port of McLib 2.4.3's ServerHandlerConfirm (roadmap P26).
 *
 * <p>Legacy behavior kept (plan open question 4, default): server confirm
 * consumers <b>never time out</b> — an unanswered modal leaks its
 * {@code Consumer<Boolean>} in the static map, exactly like 1.12.2. The
 * static map is touched from the server game thread only (post-hop, P28).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/server/ServerHandlerConfirm.java</p>
 */
public class ServerHandlerConfirm extends ServerMessageHandler<PacketConfirm>
{
    private static TreeMap<Integer, Consumer<Boolean>> consumers = new TreeMap<Integer, Consumer<Boolean>>();

    @Override
    public void run(ServerPlayerEntity player, PacketConfirm packetConfirm)
    {
        if (consumers.containsKey(packetConfirm.consumerID))
        {
            consumers.remove(packetConfirm.consumerID).accept(packetConfirm.confirm);
        }
    }

    public static void addConsumer(int id, Consumer<Boolean> item)
    {
        consumers.put(id, item);
    }

    public static Entry<Integer, Consumer<Boolean>> getLastConsumerEntry()
    {
        return consumers.lastEntry();
    }
}
