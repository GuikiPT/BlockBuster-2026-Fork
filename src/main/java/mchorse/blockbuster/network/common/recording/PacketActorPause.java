package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Actor pause/resume broadcast (roadmap P116, ledger slot 2 — clientbound).
 *
 * <p>1:1 port of 1.12.2 {@code network/common/PacketActorPause.java}. Wire
 * order: {@code id} (int), {@code pause} (bool), {@code tick} (int).</p>
 */
public class PacketActorPause implements IMessage
{
    public int id;
    public boolean pause;
    public int tick;

    public PacketActorPause()
    {}

    public PacketActorPause(int id, boolean pause, int tick)
    {
        this.id = id;
        this.pause = pause;
        this.tick = tick;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.pause = buf.readBoolean();
        this.tick = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        buf.writeBoolean(this.pause);
        buf.writeInt(this.tick);
    }
}
