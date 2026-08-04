package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.mclib.network.IMessage;
import net.minecraft.network.PacketByteBuf;

/**
 * Single-frame apply/broadcast (roadmap P116) — 1:1 wire port of the 2.7.2
 * packet: a bool-prefixed optional {@link Frame} plus the target entity id.
 *
 * <p>{@link #getFrame()} returns a defensive {@code copy()} on <b>every</b>
 * call — the server handler calls it twice (apply + body yaw), producing two
 * copies; harmless, but the accessor semantics are preserved for parity.</p>
 */
public class PacketApplyFrame implements IMessage
{
    private Frame frame;
    private int entityID;

    public PacketApplyFrame()
    {}

    public PacketApplyFrame(Frame frame, int entityID)
    {
        this.frame = frame;
        this.entityID = entityID;
    }

    public Frame getFrame()
    {
        return this.frame.copy();
    }

    public int getEntityID()
    {
        return this.entityID;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf packet = new PacketByteBuf(buf);

        if (packet.readBoolean())
        {
            this.frame = new Frame();

            this.frame.fromBytes(packet);
        }

        this.entityID = packet.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf packet = new PacketByteBuf(buf);

        packet.writeBoolean(this.frame != null);

        if (this.frame != null)
        {
            this.frame.toBytes(packet);
        }

        packet.writeInt(this.entityID);
    }
}
