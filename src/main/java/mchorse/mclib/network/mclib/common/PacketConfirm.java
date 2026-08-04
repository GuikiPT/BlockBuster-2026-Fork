package mchorse.mclib.network.mclib.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.client.gui.utils.keys.KeyParser;
import mchorse.mclib.network.IMessage;
import mchorse.mclib.network.mclib.server.ServerHandlerConfirm;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Full port of McLib 2.4.3's PacketConfirm (roadmap P26) — the server→client
 * confirm-modal round trip.
 *
 * <p>Ported quirks (kept verbatim):</p>
 * <ul>
 * <li>The server-side constructor <b>allocates the consumer id as a side
 * effect</b> (register-before-send): command code constructs the packet and
 * the callback is already stored in {@code ServerHandlerConfirm}.</li>
 * <li>Wire order is {@code langKey, gui, consumerID, confirm} — differing
 * from field declaration order; copied, not "tidied".</li>
 * <li>The client sends <b>the same packet instance</b> back with only
 * {@code confirm} mutated — so this class must work when built purely by
 * {@code fromBytes} (no constructor side effect on the echo path).</li>
 * </ul>
 *
 * <p>Split-source-set adaptation: the {@code GUI} enum lived on
 * {@code ClientHandlerConfirm} in 1.12.2, but that class is now client-only
 * while this packet (and its command-side constructors, P121) are common —
 * the enum moved here. Ported call sites change
 * {@code ClientHandlerConfirm.GUI.MCSCREEN} → {@code PacketConfirm.GUI.MCSCREEN}
 * (one-token diff, recorded in plan/network-ledger.md).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/mclib/common/PacketConfirm.java</p>
 */
public class PacketConfirm implements IMessage
{
    public int consumerID;
    public GUI gui;
    public IKey langKey;
    public boolean confirm;

    public PacketConfirm(GUI gui, IKey langKey, Consumer<Boolean> callback)
    {
        this.gui = gui;
        this.langKey = langKey;

        Map.Entry<Integer, Consumer<Boolean>> entry = ServerHandlerConfirm.getLastConsumerEntry();

        this.consumerID = (entry != null) ? entry.getKey() + 1 : 0;

        ServerHandlerConfirm.addConsumer(this.consumerID, callback);
    }

    public PacketConfirm()
    {}

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.langKey = KeyParser.keyFromBytes(buf);
        this.gui = GUI.values()[buf.readInt()];
        this.consumerID = buf.readInt();
        this.confirm = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        KeyParser.keyToBytes(buf, this.langKey);
        buf.writeInt(this.gui.ordinal());
        buf.writeInt(this.consumerID);
        buf.writeBoolean(this.confirm);
    }

    /** Legacy {@code ClientHandlerConfirm.GUI} — ordinals are on the wire. */
    public enum GUI
    {
        MCSCREEN;
    }
}
