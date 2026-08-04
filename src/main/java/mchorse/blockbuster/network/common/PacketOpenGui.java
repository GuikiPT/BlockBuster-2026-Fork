package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.GuiHandler;
import mchorse.mclib.network.IMessage;

/**
 * Server-to-client "open this GUI" payload (roadmap P100) — the Fabric
 * replacement for Forge's {@code IGuiHandler}/{@code EntityPlayer#openGui}.
 *
 * <p>1.12.2 had no packet here: {@code player.openGui(mod, ID, world, x, y, z)}
 * rode Forge's built-in GUI-open packet. Fabric has no such mechanism, so the
 * three legacy GUI ids ({@link GuiHandler#PLAYBACK 0}, {@link GuiHandler#ACTOR
 * 1}, {@link GuiHandler#MODEL_BLOCK 3} — id 2 was historically removed) travel
 * on this port-addition channel ({@code blockbuster:open_gui}, no legacy slot).
 * The four ints are the exact {@code openGui} argument shape: the GUI id plus
 * the three coordinate ints. For {@link GuiHandler#ACTOR} the entity id rides
 * in {@code x} (legacy quirk — {@code getEntityId()} passed as the GUI x), so
 * the overload semantics are preserved rather than adding a separate field.</p>
 *
 * <p>Server-bound senders only in this phase (no C2S "open for me"); the
 * dashboard's C2S opens land in S12. The client receiver routes the id through
 * {@link GuiHandler#route(int, int, int, int)}.</p>
 */
public class PacketOpenGui implements IMessage
{
    public int id;
    public int x;
    public int y;
    public int z;

    public PacketOpenGui()
    {}

    public PacketOpenGui(int id, int x, int y, int z)
    {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.id = buf.readInt();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.id);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
    }
}
