package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Request server to send list of all camera profiles to the client (P182).
 * Empty body.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketRequestCameraProfiles.java</p>
 */
public class PacketRequestCameraProfiles implements IMessage
{
    public PacketRequestCameraProfiles()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
