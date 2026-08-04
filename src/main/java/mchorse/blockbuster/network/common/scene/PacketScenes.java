package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound scene file list (roadmap P131): {@code int} count followed by that
 * many UTF8 strings. The client appends (no dedupe — the GUI clears before
 * requesting, P133). 1:1 wire port of 1.12.2 {@code PacketScenes.java}.
 */
public class PacketScenes implements IMessage
{
    public List<String> scenes = new ArrayList<String>();

    public PacketScenes()
    {}

    public PacketScenes(List<String> scenes)
    {
        this.scenes = scenes;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        for (int i = 0, c = buf.readInt(); i < c; i ++)
        {
            this.scenes.add(ForgeByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.scenes.size());

        for (String scene : this.scenes)
        {
            ForgeByteBufUtils.writeUTF8String(buf, scene);
        }
    }
}
