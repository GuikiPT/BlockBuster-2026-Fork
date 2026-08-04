package mchorse.blockbuster.network.common.audio;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.audio.AudioState;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.LatencyTimer;
import org.jetbrains.annotations.Nullable;

/**
 * Server→client scene-audio command (roadmap S16 P189). 1:1 wire port of 2.7.2
 * {@code network/common/audio/PacketAudio.java}.
 *
 * <p>Wire layout (byte-for-byte with 1.12.2): UTF8({@code audio}) +
 * int({@code state.ordinal()}) + int({@code shift}) + bool(delay-present) +
 * (when present) the 16-byte {@link LatencyTimer} (two longs).</p>
 *
 * <p>The {@code state} ordinal IS the wire contract — see {@link AudioState}.
 * Forge's {@code ByteBufUtils.readUTF8String} maps to
 * {@link ForgeByteBufUtils#readUTF8String} (P24 shim).</p>
 */
public class PacketAudio implements IMessage
{
    public String audio;
    public AudioState state;
    public int shift;

    /**
     * For syncing purposes, to clock the delay between networking and loading
     * the file.
     */
    public LatencyTimer delay;

    public PacketAudio()
    {}

    public PacketAudio(String audio, AudioState state, int shift)
    {
        this(audio, state, shift, null);
    }

    public PacketAudio(String audio, AudioState state, int shift, @Nullable LatencyTimer delay)
    {
        this.audio = audio;
        this.state = state;
        this.shift = shift;
        this.delay = delay;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.audio = ForgeByteBufUtils.readUTF8String(buf);
        this.state = AudioState.values()[buf.readInt()];
        this.shift = buf.readInt();

        if (buf.readBoolean())
        {
            this.delay = new LatencyTimer();

            this.delay.fromBytes(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        ForgeByteBufUtils.writeUTF8String(buf, this.audio);
        buf.writeInt(this.state.ordinal());
        buf.writeInt(this.shift);

        buf.writeBoolean(this.delay != null);

        if (this.delay != null)
        {
            this.delay.toBytes(buf);
        }
    }
}
