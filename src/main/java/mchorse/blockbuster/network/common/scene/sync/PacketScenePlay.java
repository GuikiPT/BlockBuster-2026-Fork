package mchorse.blockbuster.network.common.scene.sync;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.common.scene.PacketScene;
import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Packet director play (roadmap P131).
 *
 * <p>Carries the desired transport state (play/stop/pause/start/restart) plus a
 * target {@code tick}. The state constants ({@code STOP=0, PLAY=1, PAUSE=2,
 * START=3, RESTART=4}) are a wire contract — do not renumber. 1:1 wire port of
 * 1.12.2 {@code sync/PacketScenePlay.java}.</p>
 */
public class PacketScenePlay extends PacketScene
{
    public static final byte STOP = 0;
    public static final byte PLAY = 1;
    public static final byte PAUSE = 2;
    public static final byte START = 3;
    public static final byte RESTART = 4;

    public byte state;
    public int tick;

    public PacketScenePlay()
    {}

    public PacketScenePlay(SceneLocation location, byte state, int tick)
    {
        super(location);

        this.state = state;
        this.tick = tick;
    }

    public boolean isStop()
    {
        return this.state == STOP;
    }

    public boolean isPlay()
    {
        return this.state == PLAY;
    }

    public boolean isPause()
    {
        return this.state == PAUSE;
    }

    public boolean isStart()
    {
        return this.state == START;
    }

    public boolean isRestart()
    {
        return this.state == RESTART;
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeByte(this.state);
        buf.writeInt(this.tick);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.state = buf.readByte();
        this.tick = buf.readInt();
    }
}
