package mchorse.mclib.utils;

import mchorse.mclib.McLib;

import java.util.function.IntSupplier;

/**
 * Shim of McLib 2.4.3's {@code mchorse.mclib.utils.PayloadASM} (roadmap P25).
 *
 * <p>In 1.12.2 this backed a coremod that ASM-patched every {@code 32767}
 * constant in {@code PacketBuffer}/{@code CPacketCustomPayload} with
 * {@code getPayloadSize()}. <b>No ASM happens anymore</b> — on Fabric the
 * chunked transport ({@code mchorse.mclib.network.chunked.ChunkedTransport})
 * is the design — but the class, constant and method are kept so ported call
 * sites and the {@code mclib.vanilla.max_packet_size} config stay diff-able
 * against legacy.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/utils/PayloadASM.java</p>
 */
public class PayloadASM
{
    public static final int MIN_SIZE = 32767;

    /**
     * Bound to the syncable {@code mclib.vanilla.max_packet_size} config value
     * (default {@link #MIN_SIZE}, range [{@link #MIN_SIZE},
     * Integer.MAX_VALUE / 4]) — exactly legacy {@code McLib.maxPacketSize},
     * which {@code getPayloadSize()} read directly. Kept as an
     * {@link IntSupplier} seam (headless tests swap it); null-safe → MIN_SIZE,
     * like legacy's early-init null check.
     */
    public static IntSupplier maxPacketSize = () -> McLib.maxPacketSize.get();

    public static int getPayloadSize()
    {
        return Math.max(MIN_SIZE, maxPacketSize == null ? MIN_SIZE : maxPacketSize.getAsInt());
    }
}
