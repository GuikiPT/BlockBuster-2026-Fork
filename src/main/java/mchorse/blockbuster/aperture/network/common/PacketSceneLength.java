package mchorse.blockbuster.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Blockbuster aperture-bridge packet (P182): server→client reply carrying a
 * scene's length and audio shift. Wire = two big-endian ints ({@code length} =
 * {@code scene.getMaxLength()}, {@code shift} = the scene's audio shift) — the
 * S16 audio-shift trackpad in the camera editor reads {@code shift} from here.
 *
 * <p><b>Registration is deferred</b> to the S10/S11 Blockbuster dispatcher (see
 * {@link PacketRequestProfiles}); this class ports the 8-byte wire format now.
 * The client handler ({@code ClientHandlerSceneLength}) and the request/reply
 * pair ({@code PacketRequestLength}) additionally need S11's {@code PacketScene}
 * base + {@code Scene.getMaxLength} and P185.1's director-options GUI, so they
 * land with those phases.</p>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/network/common/PacketSceneLength.java</p>
 */
public class PacketSceneLength implements IMessage
{
    public int length;
    public int shift;

    public PacketSceneLength()
    {}

    public PacketSceneLength(int length, int shift)
    {
        this.length = length;
        this.shift = shift;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.length = buf.readInt();
        this.shift = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.length);
        buf.writeInt(this.shift);
    }
}
