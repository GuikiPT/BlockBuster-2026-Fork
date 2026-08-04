package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Empty-payload request for the server's scene file list (roadmap P131). The
 * server replies with {@link PacketScenes}. 1:1 wire port of 1.12.2
 * {@code PacketRequestScenes.java}.
 */
public class PacketRequestScenes implements IMessage
{
    public PacketRequestScenes()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {}

    @Override
    public void toBytes(ByteBuf buf)
    {}
}
