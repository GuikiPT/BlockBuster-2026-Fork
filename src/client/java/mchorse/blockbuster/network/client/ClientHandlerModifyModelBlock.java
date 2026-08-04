package mchorse.blockbuster.network.client;

import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.common.PacketModifyModelBlock;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Client handler for {@link PacketModifyModelBlock} (roadmap P95.1).
 *
 * <p>Applies the incoming model to the block entity at {@code pos}, but only if
 * that block is within the client's render-distance square (X/Z only — Y is
 * ignored, so a model block far above still updates). Honors the {@code merge}
 * flag (unlike the server, which always replaces) and resets the dummy actor's
 * age so the morph animation restarts after scene playback.</p>
 */
public class ClientHandlerModifyModelBlock extends ClientMessageHandler<PacketModifyModelBlock>
{
    /**
     * Pure X/Z render-range filter (legacy: drop when {@code |dx| > blockRange
     * || |dz| > blockRange}). Extracted so the filter is unit-testable without
     * a client.
     *
     * @param blockRange render distance in blocks ({@code viewDistanceChunks * 16})
     * @return {@code true} if the update should be applied
     */
    public static boolean withinRange(int blockRange, BlockPos pos, BlockPos playerPos)
    {
        BlockPos a = pos.subtract(playerPos);

        return Math.abs(a.getX()) <= blockRange && Math.abs(a.getZ()) <= blockRange;
    }

    @Override
    public void run(ClientPlayerEntity player, PacketModifyModelBlock message)
    {
        int blockRange = MinecraftClient.getInstance().options.getViewDistance().getValue() * 16;

        if (!withinRange(blockRange, message.pos, player.getBlockPos()))
        {
            return;
        }

        BlockEntity tile = player.getWorld().getBlockEntity(message.pos);

        if (tile instanceof TileEntityModel model)
        {
            model.copyData(message.model, message.merge);

            if (model.entity != null)
            {
                model.entity.age = 0;
            }
        }
    }
}
