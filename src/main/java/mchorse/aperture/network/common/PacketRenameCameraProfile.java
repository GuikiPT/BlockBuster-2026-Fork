package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Rename-profile request/echo (P182). Client→server asks to rename; the server
 * echoes the same packet back on success. Wire: UTF8 {@code from} + UTF8
 * {@code to}.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketRenameCameraProfile.java</p>
 */
public class PacketRenameCameraProfile implements IMessage
{
    public String from;
    public String to;

    public PacketRenameCameraProfile()
    {}

    public PacketRenameCameraProfile(String from, String to)
    {
        this.from = from;
        this.to = to;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.from = ForgeByteBufUtils.readUTF8String(buf);
        this.to = ForgeByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.from);
        ForgeByteBufUtils.writeUTF8String(buf, this.to);
    }
}
