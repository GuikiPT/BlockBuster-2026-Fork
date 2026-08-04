package mchorse.mclib.network.chunked;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.PayloadASM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Chunked large-payload transport (roadmap P25) — the replacement for McLib
 * 2.4.3's ASM patch of vanilla's 32,767-byte custom-payload cap (see
 * {@link PayloadASM}; the coremod approach is dead on Fabric by design).
 *
 * <p>Any encoded {@code IMessage} larger than {@link #CHUNK_SIZE} is split
 * into chunk frames sent on a reserved per-channel {@code Identifier}
 * ({@code <modid>:chunk}, ledger P23.1) and reassembled on the far side.
 * Technique studied from BBS's {@code PacketCrusher} (reference only):
 * per-transfer wire header {@code transferId, index, total, size} + raw bytes.
 * Deviations from BBS, recorded here: no {@code 69} empty-payload sentinel
 * (we assert non-empty instead), out-of-order arrival is handled via the
 * index field (MC's play channel is TCP-ordered, so disorder is only logged),
 * and reassembly enforces a total-size cap before allocating.</p>
 *
 * <p>Chunk size is <b>symmetric</b> in both directions (plan decision,
 * revisit in P214): the serverbound Fabric cap (32,767 B) is the binding
 * constraint — client→server record saves are exactly the big-payload
 * direction — so 30,000 B leaves header room under it. Chunk bytes are
 * written with raw {@code writeBytes}, never {@code writeString}, so no
 * vanilla per-field limit applies to the outer packet.</p>
 *
 * <p>This class is pure logic (netty buffers + byte arrays only) so it
 * unit-tests headlessly; the per-side senders/receivers live in
 * {@code AbstractDispatcher}.</p>
 */
public final class ChunkedTransport
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    /** Payloads strictly larger than this get chunked; each frame carries at most this many payload bytes. */
    public static final int CHUNK_SIZE = 30_000;

    /** transferId + index + total + size, four big-endian ints. */
    public static final int HEADER_SIZE = 16;

    /**
     * Internal floor for the reassembled-total cap (plan open question 7:
     * 16 MiB — largest fixture record payloads are well under 4 MiB, so this
     * keeps over 4x headroom). The legacy {@code max_packet_size} default
     * (32,767) equals the vanilla cap, so the cap must NOT be
     * {@code getPayloadSize()} alone — a default install would reject every
     * chunked transfer the transport exists for.
     */
    public static final int INTERNAL_TRANSFER_FLOOR = 16 * 1024 * 1024;

    /** Incomplete transfers older than this are swept (stale-transfer cleanup). */
    public static final long STALE_TRANSFER_TIMEOUT_MS = 30_000L;

    /** Cap on concurrent in-flight transfers per connection (memory-DoS guard). */
    public static final int MAX_CONCURRENT_TRANSFERS = 8;

    private ChunkedTransport()
    {}

    /** Server-enforced upper bound on a reassembled transfer's total size. */
    public static int maxTransferBytes()
    {
        return Math.max(INTERNAL_TRANSFER_FLOOR, PayloadASM.getPayloadSize());
    }

    /**
     * Splits {@code payload} into wire-ready chunk frames
     * ({@code [transferId, index, total, size, bytes...]}). Empty payloads are
     * a programming error — we never send empty transfers.
     */
    public static List<byte[]> split(int transferId, byte[] payload)
    {
        if (payload.length == 0)
        {
            throw new IllegalArgumentException("Empty chunked transfer (transferId " + transferId + ") — never send empty payloads");
        }

        int total = (payload.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<byte[]> frames = new ArrayList<>(total);

        for (int index = 0; index < total; index++)
        {
            int offset = index * CHUNK_SIZE;
            int size = Math.min(CHUNK_SIZE, payload.length - offset);
            ByteBuffer frame = ByteBuffer.allocate(HEADER_SIZE + size);

            frame.putInt(transferId);
            frame.putInt(index);
            frame.putInt(total);
            frame.putInt(size);
            frame.put(payload, offset, size);

            frames.add(frame.array());
        }

        return frames;
    }

    /**
     * Per-connection reassembly state. Server side keeps one per player UUID
     * (cleared on {@code ServerPlayConnectionEvents.DISCONNECT}); client side
     * keeps a single instance (cleared by P27's
     * {@code ClientNetworkState.resetHandshake()}).
     */
    public static final class Reassembler
    {
        /** Clock seam for headless staleness tests. */
        public LongSupplier clock = System::currentTimeMillis;

        private final Map<Integer, Partial> transfers = new HashMap<>();
        private final Set<Integer> rejected = new HashSet<>();

        /**
         * Consumes one chunk frame. Returns the fully reassembled payload
         * exactly once (on the final missing chunk), else {@code null}.
         * Total reader: malformed/oversized/overflowing input is logged and
         * dropped, never thrown.
         */
        public byte[] receive(ByteBuf frame)
        {
            long now = this.clock.getAsLong();

            this.sweepStale(now);

            if (frame.readableBytes() < HEADER_SIZE)
            {
                LOGGER.warn("ChunkedTransport: truncated chunk frame ({} bytes), dropping", frame.readableBytes());

                return null;
            }

            int transferId = frame.readInt();
            int index = frame.readInt();
            int total = frame.readInt();
            int size = frame.readInt();

            if (total <= 0 || index < 0 || index >= total || size < 0 || size > CHUNK_SIZE || frame.readableBytes() < size)
            {
                LOGGER.warn("ChunkedTransport: malformed chunk header (transfer {}, index {}/{}, size {}), dropping", transferId, index, total, size);

                return null;
            }

            if (this.rejected.contains(transferId))
            {
                return null;
            }

            Partial partial = this.transfers.get(transferId);

            if (partial == null)
            {
                if ((long) total * CHUNK_SIZE > maxTransferBytes())
                {
                    LOGGER.warn("ChunkedTransport: transfer {} declares {} chunks (over the {} byte cap), rejecting without allocation", transferId, total, maxTransferBytes());
                    this.reject(transferId);

                    return null;
                }

                if (this.transfers.size() >= MAX_CONCURRENT_TRANSFERS)
                {
                    LOGGER.warn("ChunkedTransport: too many concurrent transfers ({}), rejecting transfer {}", this.transfers.size(), transferId);
                    this.reject(transferId);

                    return null;
                }

                partial = new Partial(total, now);
                this.transfers.put(transferId, partial);
            }

            if (partial.total != total)
            {
                LOGGER.warn("ChunkedTransport: transfer {} changed total ({} -> {}), dropping transfer", transferId, partial.total, total);
                this.transfers.remove(transferId);
                this.reject(transferId);

                return null;
            }

            if (partial.chunks[index] != null)
            {
                LOGGER.warn("ChunkedTransport: duplicate chunk {} of transfer {}, ignoring", index, transferId);

                return null;
            }

            if (index != partial.received)
            {
                /* TCP-ordered play channel should never disorder; assert-and-log per plan */
                LOGGER.debug("ChunkedTransport: out-of-order chunk {} (expected {}) of transfer {}", index, partial.received, transferId);
            }

            byte[] data = new byte[size];

            frame.readBytes(data);
            partial.chunks[index] = data;
            partial.received += 1;

            if (partial.received < total)
            {
                return null;
            }

            this.transfers.remove(transferId);

            int length = 0;

            for (byte[] chunk : partial.chunks)
            {
                length += chunk.length;
            }

            byte[] payload = new byte[length];
            int offset = 0;

            for (byte[] chunk : partial.chunks)
            {
                System.arraycopy(chunk, 0, payload, offset, chunk.length);
                offset += chunk.length;
            }

            return payload;
        }

        public int pendingTransfers()
        {
            return this.transfers.size();
        }

        /** Disconnect cleanup — next transfers start fresh. */
        public void reset()
        {
            this.transfers.clear();
            this.rejected.clear();
        }

        private void reject(int transferId)
        {
            /* bounded — old rejections stop mattering once the sender moved on */
            if (this.rejected.size() >= 64)
            {
                this.rejected.clear();
            }

            this.rejected.add(transferId);
        }

        private void sweepStale(long now)
        {
            Iterator<Map.Entry<Integer, Partial>> it = this.transfers.entrySet().iterator();

            while (it.hasNext())
            {
                Map.Entry<Integer, Partial> entry = it.next();

                if (entry.getValue().firstSeen + STALE_TRANSFER_TIMEOUT_MS < now)
                {
                    LOGGER.warn("ChunkedTransport: transfer {} timed out incomplete ({}/{} chunks), dropping", entry.getKey(), entry.getValue().received, entry.getValue().total);
                    it.remove();
                }
            }
        }

        private static final class Partial
        {
            private final int total;
            private final byte[][] chunks;
            private final long firstSeen;
            private int received;

            private Partial(int total, long firstSeen)
            {
                this.total = total;
                this.chunks = new byte[total][];
                this.firstSeen = firstSeen;
            }
        }
    }
}
