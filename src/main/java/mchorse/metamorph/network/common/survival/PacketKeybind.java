package mchorse.metamorph.network.common.survival;

import io.netty.buffer.ByteBuf;

/**
 * Bind an acquired morph to a keybind slot (roadmap P55). Extends
 * {@link PacketIndex} with the {@code keybind} slot. Echoed back on success.
 *
 * <p>Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/network/common/survival/PacketKeybind.java</p>
 */
public class PacketKeybind extends PacketIndex
{
    public int keybind;

    public PacketKeybind()
    {
        super();
    }

    public PacketKeybind(int index, int keybind)
    {
        super(index);

        this.keybind = keybind;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.keybind = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(this.keybind);
    }
}
