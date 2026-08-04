package mchorse.mclib.network;

import io.netty.buffer.ByteBuf;

/**
 * Shim of Forge 1.12.2's
 * {@code net.minecraftforge.fml.common.network.simpleimpl.IMessage}
 * (roadmap P23). Fabric has no equivalent interface; bundling this one keeps
 * every legacy packet class portable without edits — Forge's interface had
 * exactly these two methods over netty {@link ByteBuf} (which yarn's
 * {@code PacketByteBuf} extends).
 *
 * <p>Contract inherited from 1.12.2: {@link #fromBytes} runs on the <b>netty
 * thread</b> (the buffer is released after the Fabric receiver callback
 * returns, so decoding must never be deferred); handlers then hop to the
 * game thread (P28).</p>
 */
public interface IMessage
{
    public void fromBytes(ByteBuf buf);

    public void toBytes(ByteBuf buf);
}
