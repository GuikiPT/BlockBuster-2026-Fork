package mchorse.blockbuster.network.common.scene.sync;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.common.scene.PacketScene;
import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Packet director go-to (roadmap P131). Seeks the scene to {@code tick},
 * optionally re-applying {@code actions}. 1:1 wire port of 1.12.2
 * {@code sync/PacketSceneGoto.java}.
 */
public class PacketSceneGoto extends PacketScene
{
    public int tick;
    public boolean actions;

    public PacketSceneGoto()
    {}

    public PacketSceneGoto(SceneLocation location, int tick, boolean actions)
    {
        super(location);

        this.tick = tick;
        this.actions = actions;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.tick = buf.readInt();
        this.actions = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.tick);
        buf.writeBoolean(this.actions);
    }
}
