package mchorse.blockbuster.network.server.recording;

import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.network.common.recording.PacketFramesOverwrite;
import mchorse.blockbuster.recording.RecordUtils;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Record;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.network.mclib.Dispatcher;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side ranged overwrite merge (roadmap P115).
 *
 * <p>Chunks of an overwrite range accumulate in {@link #overwriteQueue} keyed
 * by an {@link OverwriteIdentifier} that <b>sorts from/to in its constructor</b>
 * regardless of client order. {@link #insertChunk} tolerates out-of-order
 * arrival by null-padding; an overlap into non-null slots fails with
 * {@code frame_chunk_error}. Completion is when the buffer size equals
 * {@code (to - from) + 1} with no nulls; {@code toTick} is range-validated
 * against the record. Answers reuse the {@code rotation_filter} lang keys even
 * for non-rotation-filter uses.</p>
 *
 * <p>Known unresolved collision when two users overwrite the same range is a
 * legacy TODO, reproduced as-is. The client-side upload helper
 * ({@code sendFramesToServer}) is client-only and lives in the client source
 * set ({@code ClientHandlerFramesOverwrite}).</p>
 */
public class ServerHandlerFramesOverwrite extends ServerMessageHandler<PacketFramesOverwrite>
{
    /**
     * In case many people on a server want to overwrite the same record —
     * avoid collision with packets. The key identifies which record should
     * have which ticks overwritten.
     */
    private static Map<OverwriteIdentifier, List<Frame>> overwriteQueue = new HashMap<>();

    /** Headless-test seam: reset the cross-request accumulation buffer. */
    public static void clearQueue()
    {
        overwriteQueue.clear();
    }

    @Override
    public void run(ServerPlayerEntity player, PacketFramesOverwrite packet)
    {
        Result result = this.process(packet);

        if (result.answer != null)
        {
            this.sendAnswer(packet, player, result.answer, result.status);
        }
    }

    /**
     * Core overwrite state machine, decoupled from the answer send so it is
     * headless-testable. Returns the answer key + status the handler would
     * relay ({@code answer == null} means "no answer this packet" — the empty
     * chunk early-out and mid-range chunk accumulation).
     */
    public Result process(PacketFramesOverwrite packet)
    {
        Record targetRecord;
        IKey answer = null;
        boolean status = false;

        if (packet.frames.isEmpty())
        {
            System.out.println("Received an empty chunk...");

            return new Result(null, false);
        }

        try
        {
            targetRecord = CommonProxy.manager.get(packet.filename);

            if (targetRecord == null)
            {
                return new Result(IKey.format("blockbuster.error.recording.not_found", packet.filename), false);
            }
        }
        catch (Exception e)
        {
            return new Result(IKey.lang("blockbuster.gui.director.rotation_filter.record_save_error"), false);
        }

        OverwriteIdentifier key = null;

        /*
         * The constructor sorts from and to tick already.
         * It is important that from tick is always smaller than to tick, no
         * matter what the client sends!
         */
        OverwriteIdentifier targetKey = new OverwriteIdentifier(packet.getFrom(), packet.getTo(), packet.filename);

        for (Map.Entry<OverwriteIdentifier, List<Frame>> entry : overwriteQueue.entrySet())
        {
            if (entry.getKey().equals(targetKey))
            {
                key = entry.getKey();

                break;
            }
        }

        if (key == null)
        {
            key = targetKey;

            overwriteQueue.put(key, new ArrayList<>());
        }

        List<Frame> frames = overwriteQueue.get(key);

        if (this.insertChunk(packet.frames, packet.getIndex(), frames))
        {
            if (frames.size() == (key.toTick - key.fromTick) + 1 && !frames.contains(null))
            {
                if (key.toTick >= targetRecord.frames.size())
                {
                    status = false;
                    answer = IKey.lang("blockbuster.gui.director.rotation_filter.record_save_error");

                    System.out.println("toTick " + key.toTick + " out of range of record frames size.");
                }
                else
                {
                    for (int i = key.fromTick; i <= key.toTick; i++)
                    {
                        targetRecord.frames.set(i, frames.get(i - key.fromTick));
                    }

                    try
                    {
                        /* P284: a refused save must not be reported as a
                         * success — the GUI's "saved" toast is the only thing
                         * telling the user their edit reached the disk. */
                        status = RecordUtils.saveRecord(targetRecord);
                        answer = IKey.lang(status
                            ? "blockbuster.gui.director.rotation_filter.success"
                            : "blockbuster.gui.director.rotation_filter.record_save_error");
                    }
                    catch (IOException e)
                    {
                        status = false;
                        answer = IKey.lang("blockbuster.gui.director.rotation_filter.record_save_error");

                        e.printStackTrace();
                    }
                }

                overwriteQueue.remove(key);
            }
        }
        else
        {
            status = false;
            answer = IKey.lang("blockbuster.gui.director.rotation_filter.frame_chunk_error");

            overwriteQueue.remove(key);
        }

        return new Result(answer, status);
    }

    /** Outcome of {@link #process(PacketFramesOverwrite)}. */
    public static final class Result
    {
        public final IKey answer;
        public final boolean status;

        public Result(IKey answer, boolean status)
        {
            this.answer = answer;
            this.status = status;
        }
    }

    private void sendAnswer(PacketFramesOverwrite packet, ServerPlayerEntity player, IKey message, boolean status)
    {
        if (packet.getCallbackID().isPresent())
        {
            /* Legacy called ClientHandlerAnswer.sendAnswerTo, which is just
             * a send of the PacketAnswer over the mclib channel; that helper
             * is client-source-set only on Fabric, so send directly. */
            Dispatcher.sendTo(packet.getAnswer(new AbstractMap.SimpleEntry<>(message, status)), player);
        }
    }

    protected boolean insertChunk(List<Frame> chunk, int targetIndex, List<Frame> frames)
    {
        /*
         * In case packets got shuffled on the way to the server,
         * check how to insert the chunk properly.
         */
        if (targetIndex > frames.size())
        {
            Frame[] nulls = new Frame[targetIndex - frames.size()];

            frames.addAll(Arrays.asList(nulls));
            frames.addAll(chunk);
        }
        else if (targetIndex == frames.size())
        {
            frames.addAll(chunk);
        }
        else
        {
            int i = targetIndex;

            while (i < frames.size() && i < targetIndex + chunk.size())
            {
                if (frames.get(i) != null)
                {
                    break;
                }

                i++;
            }

            /* if the part in frames only contains nulls insert chunk */
            if (i == targetIndex + chunk.size())
            {
                for (int j = targetIndex; j < targetIndex + chunk.size(); j++)
                {
                    frames.set(j, chunk.get(j - targetIndex));
                }
            }
            else
            {
                /* the chunk doesn't fit in the slot because there are non null values */
                return false;
            }
        }

        return true;
    }

    /**
     * Identifier of which record should get the specified ticks overwritten.
     */
    static class OverwriteIdentifier
    {
        /** From tick to overwrite */
        int fromTick;
        /** To tick (inclusive) to overwrite to */
        int toTick;
        String filename;

        /** Sorts from and to. */
        public OverwriteIdentifier(int from, int to, String filename)
        {
            this.fromTick = Math.min(from, to);
            this.toTick = Math.max(from, to);
            this.filename = filename;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof OverwriteIdentifier)
            {
                OverwriteIdentifier framesOverwrite = (OverwriteIdentifier) obj;

                return framesOverwrite.filename.equals(this.filename)
                    && framesOverwrite.fromTick == this.fromTick
                    && framesOverwrite.toTick == this.toTick;
            }

            return false;
        }
    }
}
