package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;

/**
 * Scene management op packet (roadmap P131): rename / remove / duplicate a
 * scene by name. Wire: {@code source} string, {@code destination} string,
 * {@code int action}. Action constants start at <b>1</b> ({@code 0} is unused —
 * do not renumber). 1:1 wire port of 1.12.2 {@code PacketSceneManage.java}.
 */
public class PacketSceneManage implements IMessage
{
    public static final int RENAME = 1;
    public static final int REMOVE = 2;
    public static final int DUPE = 3;

    public String source;
    public String destination;
    public int action;

    public PacketSceneManage()
    {}

    public PacketSceneManage(String source, String destination, int action)
    {
        this.source = source;
        this.destination = destination;
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.source = ForgeByteBufUtils.readUTF8String(buf);
        this.destination = ForgeByteBufUtils.readUTF8String(buf);
        this.action = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.source);
        ForgeByteBufUtils.writeUTF8String(buf, this.destination);
        buf.writeInt(this.action);
    }
}
