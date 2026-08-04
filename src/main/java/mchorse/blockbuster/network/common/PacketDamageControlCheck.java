package mchorse.blockbuster.network.common;

import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.IMessage;
import net.minecraft.util.math.BlockPos;

/**
 * Damage-control ownership query (roadmap P131.2): client asks the server
 * "which scene's DamageControl owns this block?".
 *
 * <p><b>Wire quirk (load-bearing):</b> {@link #toBytes} writes the presence
 * boolean and then <em>always</em> writes three ints (zeros when null);
 * {@link #fromBytes} always reads all three but only constructs the
 * {@link BlockPos} when the boolean was true. Fixed 13-byte payload — preserve
 * exactly. 1:1 wire port of 1.12.2 {@code PacketDamageControlCheck.java}.</p>
 */
public class PacketDamageControlCheck implements IMessage
{
    public BlockPos pointPos;

    public PacketDamageControlCheck()
    {
        this.pointPos = null;
    }

    public PacketDamageControlCheck(BlockPos pointPos)
    {
        this.pointPos = pointPos;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        boolean havePointPos = buf.readBoolean();
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();

        if (havePointPos)
        {
            this.pointPos = new BlockPos(x, y, z);
        }
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeBoolean(this.pointPos != null);
        buf.writeInt(this.pointPos != null ? this.pointPos.getX() : 0);
        buf.writeInt(this.pointPos != null ? this.pointPos.getY() : 0);
        buf.writeInt(this.pointPos != null ? this.pointPos.getZ() : 0);
    }
}
