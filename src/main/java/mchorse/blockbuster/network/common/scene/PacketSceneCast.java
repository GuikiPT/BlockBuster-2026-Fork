package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Scene cast packet (roadmap P131). Both-side: server saves the embedded scene
 * and records it as the sender's last scene; client opens/syncs the scene
 * panel. The {@code open} flag gates the client's force-open behavior — the
 * login-sync path ({@code open(false)}, P131.1) does a silent state update
 * instead. 1:1 wire port of 1.12.2 {@code PacketSceneCast.java}.
 */
public class PacketSceneCast extends PacketScene
{
    public boolean open = true;

    public PacketSceneCast()
    {}

    public PacketSceneCast(SceneLocation location)
    {
        super(location);
    }

    public PacketSceneCast open(boolean open)
    {
        this.open = open;

        return this;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.open = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeBoolean(this.open);
    }
}
