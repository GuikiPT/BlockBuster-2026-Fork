package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;

/**
 * Request the server to reload its domain custom models (roadmap P69).
 *
 * <p>1:1 wire port of the 2.7.2 packet: a single {@code boolean force}. Ledger
 * slot 38 (SERVER) — {@code blockbuster:reload_models} (see
 * {@code mchorse.mclib.network.ChannelLedger}).</p>
 *
 * <p>Legacy source: {@code blockbuster-1.12/.../network/common/PacketReloadModels.java}.</p>
 */
public class PacketReloadModels implements IMessage
{
    public boolean force;

    public PacketReloadModels()
    {}

    public PacketReloadModels(boolean force)
    {
        this.force = force;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.force = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.force);
    }
}
