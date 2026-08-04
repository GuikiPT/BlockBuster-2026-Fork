package mchorse.mclib.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.Executor;

/**
 * Full port of McLib 2.4.3's ClientMessageHandler (roadmap P23/P28).
 *
 * <p>This class passes operation from Netty to Minecraft (Client) Thread. Also
 * prevents the server-side message handling method from appearing in client
 * message handler classes.</p>
 *
 * <p>Lives in the split <b>client source set</b> — that split replaces the
 * legacy {@code @SideOnly(Side.CLIENT)} annotations at compile time. 1.12.2's
 * {@code Minecraft.getMinecraft().addScheduledTask(...)} maps to yarn 1.20.4
 * {@code MinecraftClient.execute(Runnable)} (ReentrantThreadExecutor —
 * verified via javap; enqueues when called off the game thread, which Fabric
 * networking callbacks always are).</p>
 *
 * <p>Legacy source: .tools/legacy-src/mclib/build/sources/main/java/mchorse/mclib/network/ClientMessageHandler.java</p>
 *
 * @author Ernio (Ernest Sadowski)
 */
public abstract class ClientMessageHandler<T extends IMessage> extends AbstractMessageHandler<T>
{
    public abstract void run(final ClientPlayerEntity player, final T message);

    @Override
    public IMessage handleClientMessage(final T message)
    {
        this.getExecutor().execute(() -> ClientMessageHandler.this.run(this.getPlayer(), message));

        return null;
    }

    @Override
    public final IMessage handleServerMessage(final ServerPlayerEntity player, final T message)
    {
        return null;
    }

    /**
     * Game-thread executor seam (P28). Production path is the
     * MinecraftClient; the direct-run fallback keeps headless JUnit loopback
     * tests runnable (documented in the threading audit).
     */
    protected Executor getExecutor()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        return client != null ? client : Runnable::run;
    }

    /** Overridable for headless tests (avoids touching MinecraftClient). */
    protected ClientPlayerEntity getPlayer()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        return client != null ? client.player : null;
    }
}
