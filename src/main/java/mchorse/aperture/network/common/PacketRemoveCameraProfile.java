package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Remove-profile request/echo (P182). Client→server asks to delete; the server
 * echoes the same packet back only on a successful {@code File.delete}. Wire:
 * UTF8 profile name.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketRemoveCameraProfile.java</p>
 */
public class PacketRemoveCameraProfile implements IMessage
{
    public String profile;

    public PacketRemoveCameraProfile()
    {}

    public PacketRemoveCameraProfile(String from)
    {
        this.profile = from;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.profile = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.profile);
    }
}
