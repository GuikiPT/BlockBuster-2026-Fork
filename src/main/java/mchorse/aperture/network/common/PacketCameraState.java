package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Server→client playback control (P182). Wire: UTF8 filename +
 * {@code boolean toPlay}. An empty filename means "play whatever the client
 * has loaded" ({@code currentProfile}) — scene playback depends on it.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketCameraState.java</p>
 */
public class PacketCameraState implements IMessage
{
    public String filename = "";
    public boolean toPlay;

    public PacketCameraState()
    {}

    public PacketCameraState(boolean toPlay)
    {
        this.toPlay = toPlay;
    }

    public PacketCameraState(String filename, boolean toPlay)
    {
        this.filename = filename;
        this.toPlay = toPlay;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.toPlay = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeBoolean(this.toPlay);
    }
}
