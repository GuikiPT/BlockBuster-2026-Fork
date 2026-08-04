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
 * Full action-track push (roadmap P117). Wire: {@code filename} UTF8,
 * {@code actions.size()} int; per tick a <b>byte</b> action count
 * ({@code count == 0} encodes a null tick list) then
 * {@code ActionRegistry.toByteBuf} per action (byte-ID + payload); trailing
 * {@code open} bool (tells the client to open the record in the editor). The
 * decoder skips actions that decode to null (unknown ids), so the decoded tick
 * list can shrink — legacy accepted this.
 */
public class PacketActions implements IMessage
{
    public String filename;
    public List<List<Action>> actions;
    public boolean open;

    public PacketActions()
    {
        this.actions = new ArrayList<List<Action>>();
    }

    public PacketActions(String filename, List<List<Action>> actions, boolean open)
    {
        this.filename = filename;
        this.actions = actions;
        this.open = open;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        this.filename = ForgeByteBufUtils.readUTF8String(buf);

        for (int i = 0, c = buf.readInt(); i < c; i++)
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

        this.open = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        PacketByteBuf pbuf = buf instanceof PacketByteBuf ? (PacketByteBuf) buf : new PacketByteBuf(buf);

        ForgeByteBufUtils.writeUTF8String(buf, this.filename);
        buf.writeInt(this.actions.size());

        for (List<Action> list : this.actions)
        {
            int count = list == null ? 0 : list.size();

            /* Per-tick action count is a byte — a tick with > 127 actions
             * would corrupt the stream (Java byte sign-wrap). Legacy contract;
             * warn but keep the frozen encoding ("never happens in practice"). */
            if (count > Byte.MAX_VALUE)
            {
                Blockbuster.LOGGER.warn("PacketActions: tick has {} actions (> 127) — byte count will sign-wrap on the wire", count);
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

        buf.writeBoolean(this.open);
    }
}
