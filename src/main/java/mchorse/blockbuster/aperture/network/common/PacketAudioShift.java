package mchorse.blockbuster.aperture.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.network.common.scene.PacketScene;
import mchorse.blockbuster.recording.scene.SceneLocation;

/**
 * Client→server audio-shift edit from the camera editor's director options
 * (roadmap S16 P189, legacy Aperture-bridge slot 61). Extends the S11
 * {@link PacketScene} wire base (carrying a {@link SceneLocation}) and appends a
 * single {@code int shift}.
 *
 * <p>The legacy package split ({@code mchorse.blockbuster.aperture.network.*})
 * is kept even though Aperture is bundled — the diff-ability rule. Registered on
 * the Blockbuster channel as {@code blockbuster:audio_shift}.</p>
 *
 * <p>Legacy source:
 * {@code blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/network/common/PacketAudioShift.java}.</p>
 */
public class PacketAudioShift extends PacketScene
{
    public int shift;

    public PacketAudioShift()
    {
        super();
    }

    public PacketAudioShift(SceneLocation location, int shift)
    {
        super(location);

        this.shift = shift;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.shift = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.shift);
    }
}
