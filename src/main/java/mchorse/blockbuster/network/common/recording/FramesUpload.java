package mchorse.blockbuster.network.common.recording;

import mchorse.blockbuster.recording.data.Frame;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → server frame-upload chunk splitter (roadmap P115).
 *
 * <p>Pure port of the {@code cap = 400} chunking from legacy
 * {@code ClientHandlerPlayerRecording.sendFrames}, extracted here so the
 * transport contract is owned + headless-testable in P115 and the P116
 * recorder-stop path ({@code ClientHandlerPlayerRecording.sendFrames}) just
 * iterates {@link #split(String, List, int)} and sends each chunk over the
 * dispatcher (through the P25 chunked transport when oversized).</p>
 *
 * <p><b>Frozen quirks (pinned by tests — do not "fix"):</b></p>
 * <ul>
 * <li>The comment says "below 500" but the cap is <b>400</b>: a record with
 * {@code length < 400} ships as a single {@code PacketFramesChunk(0, 1, offset,
 * ...)} carrying the whole frame list.</li>
 * <li>Otherwise the count is {@code c = (length / cap) + 1}, and chunk {@code i}
 * carries {@code d = (length - i*cap > cap ? cap : length % cap)} frames. When
 * {@code length} is an exact multiple of {@code cap}, {@code length % cap == 0}
 * yields empty trailing chunk(s) — the server's {@code FrameChunk} still
 * expects {@code c} chunks, so the empty lists fill the remaining slots. This
 * exact behavior is a wire contract with the reassembler; changing {@code c}
 * breaks it.</li>
 * </ul>
 *
 * <p>Uses {@code frames.size()} as the length (during a recorder upload this
 * equals {@code Record.getLength()} since frames are never shorter than
 * actions), which also avoids an index overrun when actions outnumber frames.</p>
 */
public final class FramesUpload
{
    public static final int CAP = 400;

    private FramesUpload()
    {}

    public static List<PacketFramesChunk> split(String filename, List<Frame> frames, int offset)
    {
        List<PacketFramesChunk> out = new ArrayList<>();
        int cap = CAP;
        int length = frames.size();

        /* Send only one message if it's below the cap (comment says 500) */
        if (length < cap)
        {
            out.add(new PacketFramesChunk(0, 1, offset, filename, frames));

            return out;
        }

        for (int i = 0, c = (length / cap) + 1; i < c; i++)
        {
            List<Frame> chunk = new ArrayList<Frame>();

            for (int j = 0, d = length - i * cap > cap ? cap : (length % cap); j < d; j++)
            {
                chunk.add(frames.get(j + i * cap));
            }

            out.add(new PacketFramesChunk(i, c, offset, filename, chunk));
        }

        return out;
    }
}
