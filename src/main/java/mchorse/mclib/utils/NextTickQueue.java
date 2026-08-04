package mchorse.mclib.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * "Run next tick" runnable queue (roadmap P28). Legacy McLib had no single
 * class for this — it scattered {@code addScheduledTask} calls and tick-event
 * handlers; this consolidates the pattern for everything that must run
 * "next tick" rather than "later this tick" (scene playback kicks, morph
 * application after entity spawn — consumers arrive in S4+).
 *
 * <p>{@link #SERVER} is drained on Fabric's {@code END_SERVER_TICK},
 * {@link #CLIENT} on {@code END_CLIENT_TICK} (wired in the mod entrypoints).
 * The drain snapshots the queue first, so a runnable that posts another
 * runnable schedules it for the <b>following</b> drain — proper next-tick
 * semantics. Exceptions in one runnable are logged and do not kill the drain
 * (matching {@code ReentrantThreadExecutor} tolerance).</p>
 */
public final class NextTickQueue
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    public static final NextTickQueue SERVER = new NextTickQueue("server");
    public static final NextTickQueue CLIENT = new NextTickQueue("client");

    private final String name;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    public NextTickQueue(String name)
    {
        this.name = name;
    }

    /** Thread-safe; may be called from any thread (including netty). */
    public void post(Runnable runnable)
    {
        this.queue.add(runnable);
    }

    public boolean isEmpty()
    {
        return this.queue.isEmpty();
    }

    /** Called from the owning game thread once per tick (END phase). */
    public void drain()
    {
        if (this.queue.isEmpty())
        {
            return;
        }

        List<Runnable> batch = new ArrayList<>();
        Runnable runnable;

        while ((runnable = this.queue.poll()) != null)
        {
            batch.add(runnable);
        }

        for (Runnable task : batch)
        {
            try
            {
                task.run();
            }
            catch (Throwable t)
            {
                LOGGER.error("NextTickQueue({}): task threw, continuing drain", this.name, t);
            }
        }
    }

    /** Drops all pending tasks (disconnect/server-stop cleanup). */
    public void clear()
    {
        this.queue.clear();
    }
}
