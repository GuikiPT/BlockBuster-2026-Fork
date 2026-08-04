package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Client→server request to clear the player's camera capability (profile
 * {@code ""}, timestamp {@code -1}) (P182).
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketCameraReset.java</p>
 */
public class PacketCameraReset implements IMessage
{
    public PacketCameraReset()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
