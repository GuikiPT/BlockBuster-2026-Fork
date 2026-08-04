package mchorse.blockbuster.utils.watchdog;

import mchorse.blockbuster.Blockbuster;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * P92 — the <i>optional</i> {@link WatchService} folder watcher (technique
 * mirrored from BBS's {@code WatchDog}, no BBS classes imported). Strictly
 * additive over the legacy polling loops: it is only started when the new
 * {@code general.watch_files} config is enabled (default off — parity first).
 *
 * <p>Watches the Blockbuster {@code config/blockbuster/models/} and
 * {@code config/blockbuster/skins/} trees recursively. inotify (Linux) delivers
 * one event per file byte-write, so raw events are <b>coalesced/debounced</b>
 * into a single dirty-set that is only flushed once no further event has arrived
 * for {@link #DEFAULT_DEBOUNCE_MS} ms. Newly created sub-directories are
 * registered on the fly so deep additions keep being watched.</p>
 *
 * <p><b>Thread boundary.</b> This class runs entirely on its own daemon thread
 * and never touches Minecraft or GL directly — it only builds the dirty-set and
 * hands it to {@code sink}. The client wiring's sink is expected to re-schedule
 * the actual reload onto the render thread via {@code MinecraftClient.execute}
 * (which then calls {@link TextureReloader}).</p>
 *
 * <p>Kept strictly optional so parity testing stays deterministic: with the
 * config off no thread is ever created.</p>
 */
public class SkinWatcher implements Runnable
{
    /** Legacy-flavoured coalescing window (see phase sketch: "coalescing for 500 ms"). */
    public static final long DEFAULT_DEBOUNCE_MS = 500L;

    private final List<Path> roots;
    private final Consumer<Set<Path>> sink;
    private final long debounceMs;

    private WatchService service;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private Thread thread;
    private volatile boolean running;

    /* Debounce state — only touched on the watcher thread. */
    private final Set<Path> pending = new LinkedHashSet<>();
    private long lastEventNanos;

    public SkinWatcher(List<Path> roots, Consumer<Set<Path>> sink)
    {
        this(roots, sink, DEFAULT_DEBOUNCE_MS);
    }

    public SkinWatcher(List<Path> roots, Consumer<Set<Path>> sink, long debounceMs)
    {
        this.roots = new ArrayList<>(roots);
        this.sink = sink;
        this.debounceMs = Math.max(1L, debounceMs);
    }

    public boolean isRunning()
    {
        return this.running;
    }

    /** Whether the watcher's daemon thread is still alive (test/diagnostics). */
    public boolean isThreadAlive()
    {
        Thread t = this.thread;

        return t != null && t.isAlive();
    }

    public synchronized void start()
    {
        if (this.running)
        {
            return;
        }

        this.running = true;
        this.thread = new Thread(this, "Blockbuster-SkinWatcher");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public synchronized void stop()
    {
        this.running = false;

        if (this.thread != null)
        {
            this.thread.interrupt();
        }
    }

    @Override
    public void run()
    {
        try
        {
            this.service = FileSystems.getDefault().newWatchService();

            for (Path root : this.roots)
            {
                if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
                {
                    this.registerRecursive(root);
                }
            }
        }
        catch (IOException e)
        {
            Blockbuster.LOGGER.warn("Failed to start the skin file watcher", e);
            this.running = false;

            return;
        }

        while (this.running)
        {
            if (!this.pollOnce())
            {
                break;
            }
        }

        try
        {
            if (this.service != null)
            {
                this.service.close();
            }
        }
        catch (IOException ignored)
        {}
    }

    private void registerRecursive(Path path) throws IOException
    {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                SkinWatcher.this.registerFolder(dir);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerFolder(Path path)
    {
        try
        {
            WatchKey key = path.register(this.service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            this.keys.put(key, path);
        }
        catch (IOException e)
        {
            Blockbuster.LOGGER.warn("Failed to register watch folder {}", path, e);
        }
    }

    /**
     * @return {@code true} to keep looping, {@code false} to stop the watcher.
     */
    private boolean pollOnce()
    {
        WatchKey key;

        try
        {
            /* Poll frequently while a batch is pending so the debounce window is
             * honoured promptly; otherwise a lazy one-second wait. */
            long timeout = this.pending.isEmpty() ? 1000L : Math.max(1L, this.debounceMs / 4L);

            key = this.service.poll(timeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            return false;
        }
        catch (ClosedWatchServiceException e)
        {
            return false;
        }

        long now = System.nanoTime();

        if (key != null)
        {
            Path folder = this.keys.get(key);

            for (WatchEvent<?> event : key.pollEvents())
            {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW)
                {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Path name = ((WatchEvent<Path>) event).context();
                Path file = folder == null ? name : folder.resolve(name);

                /* Skip mid-write zero-length regular files (BBS idiom) — not for
                 * deletes, where the file no longer exists. */
                if (kind != StandardWatchEventKinds.ENTRY_DELETE
                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && file.toFile().length() == 0)
                {
                    continue;
                }

                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
                {
                    try
                    {
                        this.registerRecursive(file);
                    }
                    catch (IOException e)
                    {
                        Blockbuster.LOGGER.warn("Failed to register new watch folder {}", file, e);
                    }
                }

                this.pending.add(file);
                this.lastEventNanos = now;
            }

            if (!key.reset())
            {
                this.keys.remove(key);
            }
        }

        if (!this.pending.isEmpty() && (now - this.lastEventNanos) >= this.debounceMs * 1_000_000L)
        {
            this.flush();
        }

        return true;
    }

    private void flush()
    {
        Set<Path> batch = new LinkedHashSet<>(this.pending);

        this.pending.clear();

        try
        {
            this.sink.accept(batch);
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Skin watcher reload sink failed", e);
        }
    }
}
