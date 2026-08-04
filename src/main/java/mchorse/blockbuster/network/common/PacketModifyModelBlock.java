package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.mclib.network.IMessage;
import net.minecraft.util.math.BlockPos;

/**
 * Bidirectional model-block edit/sync packet (roadmap P95.1).
 *
 * <p>1:1 wire port of 1.12.2 {@code network/common/PacketModifyModelBlock.java}:
 * {@code pos} (3 ints), a {@code model != null} boolean gating an optional
 * {@link TileEntityModel#toBytes} body (settings serializer bytes + morph buf),
 * then the {@code merge} boolean. The server ignores {@code merge} (always
 * replaces); the client honors it — asymmetry is load-bearing (see the
 * handlers).</p>
 *
 * <p>Channel {@code blockbuster:modify_model_block} (ledger slots 3 C / 4 S).</p>
 */
public class PacketModifyModelBlock implements IMessage
{
    public BlockPos pos;
    public TileEntityModel model;
    public boolean merge;

    public PacketModifyModelBlock()
    {}

    public PacketModifyModelBlock(BlockPos pos, TileEntityModel model)
    {
        this.pos = pos;
        this.model = model;
    }

    public PacketModifyModelBlock(BlockPos pos, TileEntityModel model, boolean merge)
    {
        this(pos, model);

        this.merge = merge;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());

        if (buf.readBoolean())
        {
            this.model = new TileEntityModel();
            this.model.fromBytes(buf);
        }

        this.merge = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeBoolean(this.model != null);

        if (this.model != null)
        {
            this.model.toBytes(buf);
        }

        buf.writeBoolean(this.merge);
    }
}
