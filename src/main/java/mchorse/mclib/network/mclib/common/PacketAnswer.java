package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.utils.ByteBufUtils;

import java.io.Serializable;

/**
 * Full port of McLib 2.4.3's PacketAnswer (roadmap P26). Wire:
 * {@code int callBackID} + Java-serialized answer via mclib's
 * {@code ByteBufUtils.writeObject} (P14) — a deliberate legacy wart kept for
 * parity ({@code PacketBoolean} exists precisely to avoid it).
 *
 * <p>Port note: the legacy {@code catch (ClassCastException)} around the
 * unchecked cast in {@code fromBytes} is dead code (unchecked casts don't
 * throw at the cast site) — ported verbatim anyway.</p>
 *
 * <p>TODO(P24 hardening, deferred): an {@code ObjectInputFilter} allowlist for
 * the deserialization path belongs in {@code ByteBufUtils.readObject} (file
 * owned by P14); the frozen allowlist is recorded in plan/network-ledger.md.</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketAnswer.java</p>
 */
public class PacketAnswer<T extends Serializable> implements IMessage
{
    protected int callBackID;
    protected T answer;

    public PacketAnswer()
    {}

    public PacketAnswer(int callBackID, T answer)
    {
        this.callBackID = callBackID;
        this.answer = answer;
    }

    public int getCallbackID()
    {
        return this.callBackID;
    }

    public T getValue()
    {
        return this.answer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromBytes(ByteBuf buf)
    {
        this.callBackID = buf.readInt();

        try
        {
            this.answer = (T) ByteBufUtils.readObject(buf);
        }
        catch (ClassCastException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.callBackID);
        ByteBufUtils.writeObject(buf, this.answer);
    }
}
