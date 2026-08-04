package mchorse.blockbuster.utils.watchdog;

import mchorse.blockbuster.Blockbuster;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;

/**
 * P92 — the single reload-orchestration entry point shared by the dashboard
 * reload buttons (S12) and the optional {@link SkinWatcher}.
 *
 * <p>1.12.2 has no file watcher; "live reload" there is the sum of three polling
 * loops (McLib {@code FolderEntry} lazy mtime check, the picker's 1-second
 * {@code Timer}, and the 30-tick skins rescan) plus the explicit dashboard
 * "reload models/skins" buttons and the F3+T resource-reload listener. This
 * class is <b>only</b> the explicit/watcher reload path — the polling loops are
 * untouched and keep their exact legacy semantics (including the P89 GIF
 * inverted-cache gate, which polling never evicts).</p>
 *
 * <p>The actual GL work lives in subsystems that land on other branches
 * (P87 runtime texture manager, P89 GIF folders, P91 multiskin composite). To
 * stay total and independently testable, this class routes each reload through
 * pluggable <i>handler slots</i> that those subsystems install at client init;
 * every slot defaults to a no-op so a reload with nothing wired up logs and
 * returns rather than crashing.</p>
 *
 * <p><b>Re-register in place only.</b> A reload must never change the RL identity
 * of a texture: the handlers are expected to close the old GL object and
 * re-register under the <i>same</i> key, so every model still holding that
 * {@code Identifier} keeps working without being touched.</p>
 */
public final class TextureReloader
{
    /** A reload action for a single texture identifier. */
    @FunctionalInterface
    public interface ReloadHandler
    {
        void reload(Identifier id);
    }

    /**
     * The shared "nothing is installed here" handler.
     *
     * <p>Exposed (S22/P233) so a wiring test can assert a slot is <i>actually</i>
     * filled by a production installer rather than merely non-null — a seam
     * whose only writer lives in {@code src/test} is exactly the class of gap
     * S22 exists to close, and identity against this constant is the cheapest
     * way to pin it.</p>
     */
    public static final ReloadHandler NOOP = id -> {};

    /** {@link #NOOP}'s counterpart for the {@link #reloadAll()} slot. */
    public static final Runnable NOOP_ALL = () -> {};

    /**
     * SEAM(P87): close-and-re-register a plain registered texture in place.
     * Installed by the runtime texture manager when it lands.
     */
    private static ReloadHandler textureHandler = NOOP;

    /**
     * SEAM(P89): evict the mtime-keyed GIF cache entry for this file and
     * re-run the decode/upload (GifProcessThread). This is the one place the
     * inverted-cache bug gets an escape hatch <i>without</i> changing
     * {@code GifFolder} itself.
     */
    private static ReloadHandler gifHandler = NOOP;

    /**
     * SEAM(P91): re-composite any multiskin that references this file
     * (MultiskinThread.add) so layered skins refresh.
     */
    private static ReloadHandler multiskinHandler = NOOP;

    /**
     * SEAM(S12/P68-69): the dashboard "reload models/skins" whole-pack rescan.
     */
    private static Runnable reloadAllHandler = NOOP_ALL;

    private TextureReloader()
    {}

    /* Handler installation (called from client-side subsystem init) */

    public static void setTextureHandler(ReloadHandler handler)
    {
        textureHandler = handler == null ? NOOP : handler;
    }

    public static void setGifHandler(ReloadHandler handler)
    {
        gifHandler = handler == null ? NOOP : handler;
    }

    public static void setMultiskinHandler(ReloadHandler handler)
    {
        multiskinHandler = handler == null ? NOOP : handler;
    }

    public static void setReloadAllHandler(Runnable handler)
    {
        reloadAllHandler = handler == null ? NOOP_ALL : handler;
    }

    /* Handler inspection (the S22 regression pins) */

    public static ReloadHandler getTextureHandler()
    {
        return textureHandler;
    }

    public static ReloadHandler getGifHandler()
    {
        return gifHandler;
    }

    public static ReloadHandler getMultiskinHandler()
    {
        return multiskinHandler;
    }

    public static Runnable getReloadAllHandler()
    {
        return reloadAllHandler;
    }

    /**
     * Whether every one of the four slots has a real handler in it. False for
     * the shipped-but-unwired state P233 found (all four still {@link #NOOP}).
     */
    public static boolean isInstalled()
    {
        return textureHandler != NOOP
            && gifHandler != NOOP
            && multiskinHandler != NOOP
            && reloadAllHandler != NOOP_ALL;
    }

    /** Test/teardown helper: drop every installed handler back to a no-op. */
    public static void resetHandlers()
    {
        textureHandler = NOOP;
        gifHandler = NOOP;
        multiskinHandler = NOOP;
        reloadAllHandler = NOOP_ALL;
    }

    /* Reload entry points */

    /**
     * Reload a single texture identifier in place. Routes GIF identifiers to the
     * GIF handler and everything else to the plain texture handler, then always
     * offers the identifier to the multiskin handler (a changed base layer may
     * feed a composite). Totally guarded: a handler that throws logs a warning
     * and does not abort the others.
     */
    public static void reload(Identifier id)
    {
        if (id == null)
        {
            return;
        }

        String path = id.getPath().toLowerCase();

        if (path.endsWith(".gif"))
        {
            run(() -> gifHandler.reload(id), id);
        }
        else
        {
            run(() -> textureHandler.reload(id), id);
        }

        run(() -> multiskinHandler.reload(id), id);
    }

    /**
     * Reload a batch of changed files. Each path is mapped to its registration
     * {@link Identifier} through {@code mapper}; a {@code null} mapping (unknown
     * / untracked file) is skipped without error.
     */
    public static void reloadPaths(Collection<Path> paths, Function<Path, Identifier> mapper)
    {
        if (paths == null || mapper == null)
        {
            return;
        }

        for (Path path : paths)
        {
            Identifier id = null;

            try
            {
                id = mapper.apply(path);
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.warn("Failed to map changed file to a texture identifier: {}", path, e);
            }

            if (id != null)
            {
                reload(id);
            }
        }
    }

    /**
     * Whole-pack reload used by the dashboard "reload models/skins" buttons.
     */
    public static void reloadAll()
    {
        try
        {
            reloadAllHandler.run();
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to reload all textures/models", e);
        }
    }

    private static void run(Runnable action, Identifier id)
    {
        try
        {
            action.run();
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to reload texture {}", id, e);
        }
    }
}
