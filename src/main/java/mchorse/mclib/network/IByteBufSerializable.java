package mchorse.mclib.network;

import io.netty.buffer.ByteBuf;

/**
 * Port of McLib 2.4.3's {@code IByteBufSerializable} (roadmap P14/P24 seam —
 * defined in S1 so keyframes/config values compile; S2 wires it to
 * {@code PacketByteBuf}, which extends netty's {@code ByteBuf}).
 */
public interface IByteBufSerializable
{
    public void fromBytes(ByteBuf buffer);

    public void toBytes(ByteBuf buffer);
}
