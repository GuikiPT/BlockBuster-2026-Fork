package mchorse.aperture.network.common;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Server→client list of camera profile names (P182). Wire: int count + UTF8
 * names.
 *
 * <p>This same class is registered on <b>two</b> channels: Aperture's own
 * {@code aperture:camera_profile_list} and Blockbuster's bridge
 * {@code blockbuster:camera_profile_list} (ledger slot 58) — so the class must
 * not hard-code its channel; the dispatcher resolves it by
 * (dispatcher instance, class).</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/network/common/PacketCameraProfileList.java</p>
 */
public class PacketCameraProfileList implements IMessage
{
    public List<String> cameras;

    public PacketCameraProfileList()
    {
        this.cameras = new ArrayList<String>();
    }

    public PacketCameraProfileList(List<String> cameras)
    {
        this.cameras = cameras;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        for (int i = 0, c = buf.readInt(); i < c; i++)
        {
            this.cameras.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.cameras.size());

        for (String str : this.cameras)
        {
            ForgeByteBufUtils.writeUTF8String(buf, str);
        }
    }
}
