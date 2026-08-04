package mchorse.blockbuster.network.common.recording.actions;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.network.IMessage;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * DELETE/ADD/EDIT mutation of an action track (roadmap P117). Wire:
 * filename UTF8, {@code fromTick} int, {@code index} int, {@code type} ordinal
 * int, a bool-prefixed nested action-list (same byte-count framing as
 * {@link PacketActions}), then a bool-prefixed nested boolean deletion mask
 * (int size; per frame a <b>byte</b> count then bools). An <b>empty mask frame
 * (count 0) decodes as {@code [false]}</b> — symmetric with
 * {@code Record.getActionsMask}'s {@code [false]} padding for empty ticks.
 */
public class PacketActionsChange implements IMessage
{
    private String filename;
    private int fromTick = -1;
    private int index = -1;
    private List<List<Action>> actions;
    private List<List<Boolean>> mask;
    private Type type;

    public PacketActionsChange()
    {}

    /**
     * @param from order does not matter — the server sorts internally
     */
    public PacketActionsChange(String filename, int from, int index, Action action, Type type)
    {
        this.filename = filename;
        this.fromTick = from;
        this.index = index;

        List<List<Action>> actions = new ArrayList<>();
        actions.add(new ArrayList<>());
        actions.get(0).add(action);

        this.actions = actions;
        this.type = type;
    }

    public PacketActionsChange(String filename, int from, List<List<Action>> actions, Type type)
    {
        this.filename = filename;
        this.fromTick = from;
        this.actions = actions;
        this.type = type;
    }

    public PacketActionsChange(String filename, int tick, int index, List<List<Action>> actions, Type type)
    {
        this.filename = filename;
        this.fromTick = tick;
        this.index = index;
        this.actions = actions;
        this.type = type;
    }

    /**
     * A deletion package
     */
    public PacketActionsChange(String filename, int tick, List<List<Boolean>> deletionMask)
    {
        this(filename, tick, null, Type.DELETE);

        this.mask = deletionMask;
    }

    public boolean containsOneAction()
    {
        return this.actions != null && !this.actions.isEmpty() && this.actions.get(0) != null && !this.actions.get(0).isEmpty();
    }

    public int getIndex()
    {
        return this.index;
    }

    public int getFromTick()
    {
        return this.fromTick;
    }

    public List<List<Action>> getActions()
    {
        return this.actions;
    }

    public String getFilename()
    {
        return this.filename;
    }

    public Type getStatus()
    {
        return this.type;
    }

    public List<List<Boolean>> getMask()
    {
        return this.mask;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.filename = ForgeByteBufUtils.readUTF8String(buf);
        this.fromTick = buf.readInt();
        this.index = buf.readInt();
        this.type = Type.values()[buf.readInt()];

        if (buf.readBoolean())
        {
            this.actions = new ArrayList<>();
            int size = buf.readInt();

            for (int i = 0; i < size; i++)
            {
                int count = buf.readByte();

                if (count != 0)
                {
                    List<Action> actions = new ArrayList<Action>();

                    for (int j = 0; j < count; j++)
                    {
                        Action action = ActionRegistry.fromByteBuf(pbuf);

                        if (action != null)
                        {
                            actions.add(action);
                        }
                    }

                    this.actions.add(actions);
                }
                else
                {
                    this.actions.add(null);
                }
            }
        }

        if (buf.readBoolean())
        {
            this.mask = new ArrayList<>();

            int size = buf.readInt();

            for (int i = 0; i < size; i++)
            {
                List<Boolean> maskFrame = new ArrayList<>();

                int count = buf.readByte();

                if (count != 0)
                {
                    for (int j = 0; j < count; j++)
                    {
                        maskFrame.add(buf.readBoolean());
                    }
                }
                else
                {
                    maskFrame.add(false);
                }

                this.mask.add(maskFrame);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeInt(this.fromTick);
        buf.writeInt(this.index);
        buf.writeInt(this.type.ordinal());
        buf.writeBoolean(this.actions != null);

        if (this.actions != null)
        {
            buf.writeInt(this.actions.size());

            for (List<Action> list : this.actions)
            {
                int count = list == null ? 0 : list.size();

                if (count > Byte.MAX_VALUE)
                {
                    Blockbuster.LOGGER.warn("PacketActionsChange: tick has {} actions (> 127) — byte count will sign-wrap on the wire", count);
                }

                buf.writeByte(count);

                if (count != 0)
                {
                    for (Action action : list)
                    {
                        ActionRegistry.toByteBuf(action, pbuf);
                    }
                }
            }
        }

        buf.writeBoolean(this.mask != null);

        if (this.mask != null)
        {
            buf.writeInt(this.mask.size());

            for (List<Boolean> list : this.mask)
            {
                int count = list == null ? 0 : list.size();

                buf.writeByte(count);

                if (count != 0)
                {
                    for (Boolean bool : list)
                    {
                        buf.writeBoolean(bool);
                    }
                }
            }
        }
    }

    public enum Type
    {
        DELETE,
        ADD,
        EDIT
    }
}
