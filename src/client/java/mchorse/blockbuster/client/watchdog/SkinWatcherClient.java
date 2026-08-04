package mchorse.blockbuster.client.watchdog;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.client.ActorsPack;
import mchorse.blockbuster.utils.BlockbusterPaths;
import mchorse.blockbuster.utils.watchdog.SkinWatcher;
import mchorse.blockbuster.utils.watchdog.TextureReloader;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * P92 — client-side bootstrap for the optional {@link SkinWatcher}. Reads the
 * {@code general.watch_files} config (default off): when enabled it starts one
 * watcher over {@code config/blockbuster/models/} + {@code config/blockbuster/skins/}
 * whose flushed dirty-set is re-scheduled onto the render thread through
 * {@link MinecraftClient#execute(Runnable)} and handed to {@link TextureReloader}.
 *
 * <p>When the config is off no watcher is created, so parity behaviour (the
 * three legacy polling loops only) is byte-for-byte unchanged.</p>
 *
 * <p>S22/P233 filled in the path&rarr;{@link Identifier} half (see
 * {@link #pathToId(Path)}) and the texture/model split (see
 * {@link #apply(Collection)}); the handlers those route into are installed by
 * {@link TextureReloaderWiring}.</p>
 */
public final class SkinWatcherClient
{
    /**
     * Files whose change is a <b>texture</b> change — mapped to a {@code b.a}
     * identifier and pushed through {@link TextureReloader#reloadPaths}. The
     * legacy skin/GIF pipeline only ever served these.
     */
    private static final List<String> TEXTURE_EXTENSIONS = Arrays.asList(".png", ".gif", ".jpg", ".jpeg");

    /**
     * Files whose change is a <b>model pack</b> change — one whole-pack rescan
     * per flushed batch ({@link TextureReloader#reloadAll()}), because
     * {@code ModelPack} is mtime-driven and re-reads only what actually moved.
     * {@code .json} covers both {@code model.json} and the Snowstorm
     * {@code *.particle.json} schemes living under the same tree.
     */
    private static final List<String> MODEL_EXTENSIONS = Arrays.asList(".json", ".obj", ".mtl", ".vox");

    private static SkinWatcher watcher;

    private SkinWatcherClient()
    {}

    /**
     * Map a changed file under one of {@link ActorsPack}'s lookup folders to the
     * {@link Identifier} it is served under.
     *
     * <p>This is the exact inverse of {@code ActorsPack.buildPackFile}: that
     * resolves a verbatim {@code b.a} path to {@code new File(folder, path)}
     * walking {@link ActorsPack#folders} in order, so the mapper relativizes
     * against the same list (first containing folder wins) and hands the result
     * to {@link ResourceLocation#toIdentifier()} — the one place the port
     * sanitizes into the vanilla texture map's alphabet, so the key produced
     * here is identical to the key the texture was registered under.</p>
     *
     * <p>Purely lexical: {@link Path#toAbsolutePath()} + {@link Path#normalize()}
     * never touch the filesystem, so a file <b>deleted between the watch event
     * and the reload</b> still maps (and its stale texture still gets evicted)
     * instead of throwing.</p>
     *
     * <p>Total: a path under no lookup folder yields {@code null} and is skipped
     * by {@link TextureReloader#reloadPaths}. The most common such path is
     * anything sitting in the {@code config/blockbuster/skins} drop folder,
     * which {@code SkinHandler} auto-sorts into a model folder rather than
     * serving directly — the move itself then fires a second, mappable event.</p>
     *
     * @return the {@code b.a} identifier, or {@code null} when unmappable
     */
    public static Identifier pathToId(Path path)
    {
        if (path == null)
        {
            return null;
        }

        try
        {
            Path file = path.toAbsolutePath().normalize();
            List<File> folders = ActorsPack.folders.get();

            if (folders == null)
            {
                return null;
            }

            for (File folder : folders)
            {
                if (folder == null)
                {
                    continue;
                }

                Path root = folder.toPath().toAbsolutePath().normalize();

                if (file.equals(root) || !file.startsWith(root))
                {
                    continue;
                }

                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');

                if (relative.isEmpty())
                {
                    continue;
                }

                return new ResourceLocation(ActorsPack.DOMAIN, relative).toIdentifier();
            }
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to map the changed file {} into the b.a namespace", path, e);
        }

        return null;
    }

    /**
     * Starts the watcher if {@code general.watch_files} is enabled. Idempotent —
     * a second call while already running is a no-op. Returns the active watcher
     * (or {@code null} when disabled) so callers/tests can observe the decision.
     */
    public static synchronized SkinWatcher start()
    {
        if (!isEnabled())
        {
            return null;
        }

        if (watcher != null && watcher.isRunning())
        {
            return watcher;
        }

        watcher = new SkinWatcher(
            Arrays.asList(BlockbusterPaths.models(), BlockbusterPaths.skins()),
            SkinWatcherClient::onChanged);

        watcher.start();

        Blockbuster.LOGGER.info("Blockbuster skin file watcher started (general.watch_files enabled)");

        return watcher;
    }

    public static synchronized void stop()
    {
        if (watcher != null)
        {
            watcher.stop();
            watcher = null;
        }
    }

    public static boolean isEnabled()
    {
        return Blockbuster.watchFiles != null && Blockbuster.watchFiles.get();
    }

    /**
     * Watcher-thread callback: hop to the render thread, then run the shared
     * reload path. Never does GL work on the watcher thread.
     */
    private static void onChanged(Set<Path> changed)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        mc.execute(() -> apply(changed));
    }

    /**
     * Render-thread half of a flushed batch: textures go through the per-file
     * reload (which routes GIFs to the GIF handler and offers everything to the
     * multiskin handler), model-pack files collapse into a <b>single</b>
     * whole-pack rescan for the whole batch, and everything else — directory
     * creations, editor swap files, {@code .mcmeta} — is ignored.
     *
     * <p>Package-visible rather than private so the wiring test can drive a
     * synthetic batch without a {@link MinecraftClient}.</p>
     */
    static void apply(Collection<Path> changed)
    {
        if (changed == null || changed.isEmpty())
        {
            return;
        }

        List<Path> textures = new ArrayList<Path>();
        boolean pack = false;

        for (Path path : changed)
        {
            String name = name(path);

            if (matches(name, TEXTURE_EXTENSIONS))
            {
                textures.add(path);
            }
            else if (matches(name, MODEL_EXTENSIONS))
            {
                pack = true;
            }
        }

        if (!textures.isEmpty())
        {
            TextureReloader.reloadPaths(textures, SkinWatcherClient::pathToId);
        }

        if (pack)
        {
            TextureReloader.reloadAll();
        }
    }

    private static String name(Path path)
    {
        Path name = path == null ? null : path.getFileName();

        return name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String name, List<String> extensions)
    {
        for (String extension : extensions)
        {
            if (name.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }
}
