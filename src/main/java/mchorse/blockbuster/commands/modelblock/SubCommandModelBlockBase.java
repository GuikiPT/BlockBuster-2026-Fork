package mchorse.blockbuster.commands.modelblock;

import mchorse.blockbuster.commands.BBCommandBase;
import mchorse.blockbuster.common.tileentity.TileEntityModel;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.mclib.network.IMessage;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import mchorse.mclib.commands.utils.CommandException;

/**
 * Base for {@code /modelblock} subcommands — port of legacy
 * {@code commands.modelblock.SubCommandModelBlockBase} (roadmap P207.4).
 *
 * <p>Resolves a {@link TileEntityModel} at the plain-integer {@code x y z}
 * (args[0..2]) — <b>no {@code ~} relative support</b>, matching legacy
 * {@code CommandBase.parseInt} — or throws {@code modelblock.missing} with the
 * coordinates. The 64-block radius re-broadcast that legacy did through
 * {@code Dispatcher.sendToAllAround(msg, TargetPoint(dim, x, y, z, 64))} is
 * reproduced by {@link #broadcastAround} iterating the sender's dimension.</p>
 */
public abstract class SubCommandModelBlockBase extends BBCommandBase
{
    @Override
    public int getRequiredArgs()
    {
        return 3;
    }

    public TileEntityModel getModelBlock(ServerCommandSource sender, String[] args) throws CommandException
    {
        int x = parseInt(args[0]);
        int y = parseInt(args[1]);
        int z = parseInt(args[2]);

        World world = sender.getWorld();
        BlockEntity tile = world.getBlockEntity(new BlockPos(x, y, z));

        if (tile instanceof TileEntityModel)
        {
            return (TileEntityModel) tile;
        }

        throw new CommandException("modelblock.missing", x, y, z);
    }

    /**
     * Send {@code message} to every player within 64 blocks of {@code pos} in
     * the sender's dimension — the port equivalent of legacy
     * {@code sendToAllAround(..., new TargetPoint(dim, x, y, z, 64))}.
     */
    protected static void broadcastAround(ServerCommandSource sender, BlockPos pos, IMessage message)
    {
        ServerWorld world = sender.getWorld();

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double radiusSq = 64.0D * 64.0D;

        for (ServerPlayerEntity player : world.getPlayers())
        {
            if (player.squaredDistanceTo(x, y, z) <= radiusSq)
            {
                Dispatcher.sendTo(message, player);
            }
        }
    }
}
