package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Client→server request to load a server-stored profile (P182). Wire: UTF8
 * filename + {@code boolean force}.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketLoadCameraProfile.java</p>
 */
public class PacketLoadCameraProfile implements IMessage
{
    public String filename;
    public boolean force;

    public PacketLoadCameraProfile()
    {}

    public PacketLoadCameraProfile(String filename, boolean force)
    {
        this.filename = filename;
        this.force = force;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.force = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeBoolean(this.force);
    }
}
