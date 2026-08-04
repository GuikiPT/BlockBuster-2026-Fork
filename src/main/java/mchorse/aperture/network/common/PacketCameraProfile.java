package mchorse.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.aperture.camera.CameraProfile;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Camera profile transfer (P182). Same class both directions: server→client
 * carries a profile to load/play, client→server saves one. Wire:
 * {@code boolean play} + UTF8 filename + full profile via
 * {@code StructureBase.toBytes/fromBytes}. Full profiles (ManualFixture
 * frames!) can exceed the 32,767-byte serverbound cap, so this packet rides
 * the S2 chunked transport transparently.
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketCameraProfile.java</p>
 */
public class PacketCameraProfile implements IMessage
{
    public boolean play;
    public String filename;
    public CameraProfile profile;

    public PacketCameraProfile()
    {}

    public PacketCameraProfile(String filename, CameraProfile profile)
    {
        this(filename, profile, false);
    }

    public PacketCameraProfile(String filename, CameraProfile profile, boolean play)
    {
        this.play = play;
        this.filename = filename;
        this.profile = profile;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.play = buf.readBoolean();
        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.profile = new CameraProfile(null);
        this.profile.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.play);
        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        this.profile.toBytes(buf);
    }
}
