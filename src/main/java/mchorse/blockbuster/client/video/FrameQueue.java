package mchorse.blockbuster.client.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Bounded, single-consumer encode queue that decouples GL readback (render
 * thread) from the sink write (encoder thread) — P200 async pipeline.
 *
 * <p>A fixed pool of reusable direct buffers bounds memory and enforces
 * <b>backpressure</b>: {@link #submit} takes a free buffer, blocking the render
 * thread when the encoder is behind, rather than dropping frames — the
 * frame-perfect guarantee (P199) beats realtime. FIFO order is preserved by the
 * single consumer; buffers are returned to the free pool after each write, so
 * total allocation never exceeds {@code capacity}.</p>
 */
public class FrameQueue
{
    private static final Logger LOGGER = LoggerFactory.getLogger("blockbuster-video");

    /** Sentinel put on the full queue by {@link #stop} to end the consumer loop. */
    private static final ByteBuffer POISON = ByteBuffer.allocate(0);

    private final FrameSink sink;
    private final BlockingQueue<ByteBuffer> full;
    private final BlockingQueue<ByteBuffer> free;
    private final int capacity;
    private final int frameSize;

    private Thread thread;
    private volatile IOException error;

    public FrameQueue(FrameSink sink, int capacity, int frameSize)
    {
        this.sink = sink;
        this.capacity = Math.max(1, capacity);
        this.frameSize = frameSize;
        this.full = new ArrayBlockingQueue<>(this.capacity + 1);
        this.free = new ArrayBlockingQueue<>(this.capacity);

        for (int i = 0; i < this.capacity; i++)
        {
            this.free.add(ByteBuffer.allocateDirect(frameSize));
        }
    }

    /** Number of buffers allocated (fixed at {@code capacity} — no growth). */
    public int allocatedBuffers()
    {
        return this.capacity;
    }

    /** Start the consumer thread. Idempotent-safe: call once after construction. */
    public void start()
    {
        this.thread = new Thread(this::run, "blockbuster-video-encoder");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void run()
    {
        try
        {
            while (true)
            {
                ByteBuffer buffer = this.full.take();

                if (buffer == POISON)
                {
                    return;
                }

                try
                {
                    this.sink.frame(buffer);
                }
                finally
                {
                    buffer.clear();
                    this.free.offer(buffer);
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (IOException e)
        {
            this.error = e;
            LOGGER.error("Video encoder sink failed", e);
        }
    }

    /**
     * Copy one frame into a pooled buffer and enqueue it, blocking (backpressure)
     * while the encoder catches up. {@code src} is fully consumed. Returns
     * {@code false} if the encoder has already failed (caller should stop the
     * recording — the P202 premature-stop path).
     */
    public boolean submit(ByteBuffer src) throws InterruptedException
    {
        if (this.error != null)
        {
            return false;
        }

        ByteBuffer buffer = this.free.take();

        buffer.clear();
        buffer.put(src);
        buffer.flip();
        this.full.put(buffer);

        return this.error == null;
    }

    /** The failure captured by the encoder thread, if any (P202 stop path). */
    public IOException error()
    {
        return this.error;
    }

    /**
     * Drain remaining frames, stop the consumer, and join it. Does not close the
     * sink — the recorder owns {@link FrameSink#end()} ordering.
     */
    public void stop()
    {
        try
        {
            this.full.put(POISON);

            if (this.thread != null)
            {
                this.thread.join();
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
