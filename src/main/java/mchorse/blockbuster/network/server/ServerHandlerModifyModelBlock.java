package mchorse.blockbuster.network.server;

import mchorse.blockbuster.common.block.BlockModel;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.mclib.network.ServerMessageHandler;
import mchorse.mclib.utils.OpHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Server handler for {@link PacketModifyModelBlock} (roadmap P95.1).
 *
 * <p>OP-gated. On a {@link TileEntityModel} it {@code copyData(model, false)}
 * (the {@code merge} flag is <b>ignored</b> here — the server always replaces),
 * pushes the settings' light value into the blockstate with flag {@code 2}
 * ({@link Block#NOTIFY_LISTENERS} — "important for servers"), then re-broadcasts
 * the packet <b>as received</b> (original merge flag intact) to every player in
 * the sender's dimension, including the sender — exactly like 1.12.2.</p>
 */
public class ServerHandlerModifyModelBlock extends ServerMessageHandler<PacketModifyModelBlock>
{
    @Override
    public void run(ServerPlayerEntity player, PacketModifyModelBlock message)
    {
        if (!OpHelper.isPlayerOp(player))
        {
            return;
        }

        if (message.model == null)
        {
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockEntity te = getBlockEntitySafely(world, message.pos);

        if (te instanceof TileEntityModel model)
        {
            model.copyData(message.model, false);

            BlockState state = world.getBlockState(message.pos);

            if (state.getBlock() instanceof BlockModel)
            {
                world.setBlockState(message.pos,
                    state.with(BlockModel.LIGHT, model.getSettings().getLightValue()),
                    Block.NOTIFY_LISTENERS);
            }

            Dispatcher.sendToDimension(message, world);
        }
    }
}
