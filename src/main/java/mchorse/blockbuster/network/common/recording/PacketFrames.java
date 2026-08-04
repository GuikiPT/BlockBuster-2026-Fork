package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet responsible for delivering recorded frames either to the server for
 * saving or to the client for playback (roadmap P115) — 1:1 port of 2.7.2.
 *
 * <p>Wire form of the base: {@code filename} UTF8, {@code preDelay} int,
 * {@code postDelay} int, then a <b>bool-prefixed</b> frame list
 * ({@code writeBoolean(frames != null)}; count int + {@code Frame.toBytes}
 * each). A <b>null frames list is legal and distinct from empty</b> — the flag
 * bit encodes that difference. Subclasses prepend/append fields; ordering is
 * the frozen contract.</p>
 *
 * <p>Frames delegate to {@link Frame#toBytes(PacketByteBuf)} /
 * {@link Frame#fromBytes(PacketByteBuf)}; the incoming {@link ByteBuf} is the
 * dispatcher's {@code PacketByteBuf} at runtime but declared as {@code ByteBuf}
 * by the {@link IMessage} contract, so it is wrapped when needed.</p>
 */
public abstract class PacketFrames implements IMessage
{
    public String filename;
    public int preDelay;
    public int postDelay;
    public List<Frame> frames;

    public PacketFrames()
    {}

    public PacketFrames(String filename, int preDelay, int postDelay, List<Frame> frames)
    {
        this.filename = filename;
        this.preDelay = preDelay;
        this.postDelay = postDelay;
        this.frames = frames;
    }

    /** Wrap a raw ByteBuf as a PacketByteBuf without copying (shared indices). */
    protected static PacketByteBuf wrap(ByteBuf buf)
    {
        return buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = wrap(buf);
        List<Frame> frames = new ArrayList<Frame>();

        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.preDelay = buf.readInt();
        this.postDelay = buf.readInt();

        if (buf.readBoolean())
        {
            int count = buf.readInt();

            for (int i = 0; i < count; i++)
            {
                Frame frame = new Frame();

                frame.fromBytes(pbuf);
                frames.add(frame);
            }

            this.frames = frames;
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = wrap(buf);

        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeInt(this.preDelay);
        buf.writeInt(this.postDelay);
        buf.writeBoolean(this.frames != null);

        if (this.frames != null)
        {
            buf.writeInt(this.frames.size());

            for (Frame frame : this.frames)
            {
                frame.toBytes(pbuf);
            }
        }
    }
}
