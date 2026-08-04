package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Empty server-mode handshake (P182). The client handler flips
 * {@code ClientProxy.server = true} so the profile manager knows the server
 * runs Aperture and can proxy profiles.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketAperture.java</p>
 */
public class PacketAperture implements IMessage
{
    public PacketAperture()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
