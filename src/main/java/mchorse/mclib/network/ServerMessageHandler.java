package mchorse.mclib.network;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.concurrent.Executor;

/**
 * Full port of McLib 2.4.3's ServerMessageHandler (roadmap P23/P28).
 *
 * <p>This class passes operation from Netty to Minecraft (Server) Thread. This
 * class will prevent the client-side message handling method from appearing in
 * server message handler classes.</p>
 *
 * <p>1.12.2's {@code player.getServer().addScheduledTask(...)} maps to yarn
 * 1.20.4 {@code MinecraftServer.execute(Runnable)} (inherited from
 * {@code ThreadExecutor} — verified via javap; runs immediately when already on
 * the game thread, else enqueues: same semantics as {@code addScheduledTask}).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/ServerMessageHandler.java</p>
 *
 * @author Ernio (Ernest Sadowski)
 */
public abstract class ServerMessageHandler<T extends IMessage> extends AbstractMessageHandler<T>
{
    public abstract void run(final ServerPlayerEntity player, final T message);

    @Override
    public IMessage handleServerMessage(final ServerPlayerEntity player, final T message)
    {
        this.getExecutor(player).execute(() -> ServerMessageHandler.this.run(player, message));

        return null;
    }

    @Override
    public final IMessage handleClientMessage(final T message)
    {
        return null;
    }

    /**
     * Game-thread executor seam (P28). Production path is always
     * {@code player.getServer()}; the fallbacks keep headless JUnit loopback
     * tests (null player, no server) runnable without a live server —
     * documented in the threading audit (plan/network-ledger.md).
     */
    protected Executor getExecutor(ServerPlayerEntity player)
    {
        MinecraftServer server = player != null ? player.getServer() : AbstractDispatcher.getServer();

        return server != null ? server : Runnable::run;
    }

    /**
     * Safe way to get a block entity on the server without exposing code
     * to ACG (Arbitrary Chunk Generation) exploit (thanks to Paul Fulham).
     *
     * <p>1.12.2 {@code world.isBlockLoaded(pos)} maps to yarn 1.20.4
     * {@code WorldView.isChunkLoaded(BlockPos)} (verified via javap — the
     * BlockPos overload delegates to {@code isChunkLoaded(chunkX, chunkZ)}
     * without generating the chunk); only then {@code world.getBlockEntity}.</p>
     */
    protected BlockEntity getTE(ServerPlayerEntity player, BlockPos pos)
    {
        return getBlockEntitySafely(player.getWorld(), pos);
    }

    /**
     * The pure guard predicate behind {@link #getTE}, exposed for headless
     * testing (constructing a real {@code World} is not possible in unit
     * tests; the guard logic itself is what matters).
     */
    public static BlockEntity getBlockEntitySafely(World world, BlockPos pos)
    {
        if (world != null && world.isChunkLoaded(pos))
        {
            return world.getBlockEntity(pos);
        }

        return null;
    }
}
