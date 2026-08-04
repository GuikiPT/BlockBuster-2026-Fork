package mchorse.blockbuster.network.client.recording;

import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.PacketFramesOverwrite;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.network.mclib.client.ClientHandlerAnswer;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side upload half of the ranged overwrite flow (roadmap P115).
 *
 * <p>Legacy kept these statics on {@code ServerHandlerFramesOverwrite}; on
 * Fabric the answer framework ({@code ClientHandlerAnswer}) is client-source
 * only, so the client uploader lives here. Chunks the range at
 * {@code cap = 400} with a running {@code chunkStart}; each chunk becomes a
 * {@link PacketFramesOverwrite} routed to the server (through the P25 chunked
 * transport when oversized). S12's editor calls these 1:1.</p>
 */
public final class ClientHandlerFramesOverwrite
{
    private ClientHandlerFramesOverwrite()
    {}

    public static void sendFramesToServer(String filename, List<Frame> frames, int from, int to)
    {
        sendFramesToServer(filename, frames, from, to, null);
    }

    /**
     * @param callback called when the server's answer returns (or null for
     *                 fire-and-forget)
     */
    public static void sendFramesToServer(String filename, List<Frame> frames, int from, int to,
                                          @Nullable Consumer<AbstractMap.SimpleEntry<IKey, Boolean>> callback)
    {
        int cap = 400;

        if (frames.size() <= cap)
        {
            if (callback != null)
            {
                ClientHandlerAnswer.requestServerAnswer(Dispatcher.DISPATCHER,
                    new PacketFramesOverwrite(from, to, 0, filename, frames), callback);
            }
            else
            {
                Dispatcher.sendToServer(new PacketFramesOverwrite(from, to, 0, filename, frames));
            }

            return;
        }

        List<Frame> chunk = new ArrayList<>();
        int chunkStart = 0;

        for (int i = 0; i < frames.size(); i++)
        {
            chunk.add(frames.get(i));

            if (chunk.size() == cap || i == frames.size() - 1)
            {
                if (callback != null)
                {
                    ClientHandlerAnswer.requestServerAnswer(Dispatcher.DISPATCHER, new PacketFramesOverwrite(from, to, chunkStart, filename, chunk), callback);
                }
                else
                {
                    Dispatcher.sendToServer(new PacketFramesOverwrite(from, to, chunkStart, filename, chunk));
                }

                chunk.clear();

                chunkStart += cap;
            }
        }
    }
}
