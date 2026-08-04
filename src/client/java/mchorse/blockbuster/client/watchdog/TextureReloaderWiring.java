package mchorse.blockbuster.client.watchdog;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.CommonProxy;
import mchorse.blockbuster.client.ActorsPack;
import mchorse.blockbuster.client.model.parsing.ModelExtrudedLayer;
import mchorse.blockbuster.client.textures.TextureRegistry;
import mchorse.blockbuster.utils.mclib.GifFolder;
import mchorse.blockbuster.utils.watchdog.TextureReloader;
import mchorse.mclib.utils.resources.MultiResourceLocation;
import mchorse.mclib.utils.resources.MultiResourceLocationManager;
import mchorse.mclib.utils.resources.MultiskinThread;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S22/P233 — the production installer for the four {@link TextureReloader}
 * handler slots.
 *
 * <p>P92 shipped the orchestration ({@code TextureReloader}) and the watcher
 * ({@code SkinWatcher}/{@link SkinWatcherClient}) with every slot defaulting to
 * a no-op, to be filled by P87 (runtime texture registry), P89 (GIF folders),
 * P91 (multiskin composite) and S12/P68-69 (whole-pack rescan) as those landed.
 * They landed; nobody came back. This class is that missing assignment — one
 * {@link #install()} called from exactly one line of {@code BlockbusterClient},
 * so live reload is reachable end to end.</p>
 *
 * <h3>What each slot does</h3>
 * <ul>
 * <li><b>texture</b> — evict the identifier from the vanilla texture map,
 * closing its GL/native object, and drop any extruded 3D layer keyed on it.
 * 1.20.4's {@code TextureManager.getTexture} lazily rebuilds an evicted
 * identifier from the resource manager on the next bind, and that lookup is
 * routed back through {@link ActorsPack} by the resource-manager mixin — so
 * eviction <i>is</i> "close and re-register in place": the {@link Identifier}
 * every model holds keeps working and simply resolves to fresh disk bytes.</li>
 * <li><b>gif</b> — additionally evicts {@link GifFolder}'s mtime-keyed decode
 * cache (the one documented escape hatch from P89's inverted-staleness quirk,
 * which otherwise never hot-reloads an edited GIF within a session) and every
 * {@code <path>_/frameN.png} frame texture registered by
 * {@code GifProcessThread}. Eviction is the whole job on this side; whether the
 * re-decode then happens is P231's (the GIF open-time scheduling seam is still
 * uninstalled at the time of writing, so today a changed GIF goes stale-free
 * rather than animated-fresh — strictly better than the cached ghost).</li>
 * <li><b>multiskin</b> — re-enqueues every registered
 * {@link MultiResourceLocation} that lists the changed file among its children,
 * so a layered skin re-composites from disk. The composite's own texture object
 * is deliberately <i>not</i> evicted: {@code MultiskinThread}'s uploader
 * uploads into the existing GL object and bails when it is gone.</li>
 * <li><b>reloadAll</b> — the client-local half of {@code /model reload}
 * ({@code CommonProxy.loadModels(false)}), i.e. the dashboard "reload
 * models/skins" whole-pack rescan.</li>
 * </ul>
 *
 * <p><b>Thread boundary.</b> Every handler here does map surgery and/or GL
 * teardown and must run on the render thread. {@link SkinWatcherClient} hops
 * through {@code MinecraftClient.execute} before calling into
 * {@link TextureReloader}; nothing in this class re-marshals.</p>
 *
 * <p><b>Total.</b> Every handler tolerates a missing client, a missing texture,
 * a file deleted between the watch event and the reload, and an unresolvable
 * identifier — it logs and returns. {@code TextureReloader} guards each call
 * anyway, but the handlers do not lean on that.</p>
 */
public final class TextureReloaderWiring
{
    /**
     * The live vanilla texture map, wrapped in the P87 registry. Overridable so
     * the headless tests can drive the real handlers against a plain
     * {@link Map} — the alternative (asserting only that {@code install()} put
     * <i>something</i> in the slot) would not have caught the P233 gap's twin,
     * a handler installed but wired to the wrong map.
     */
    public static Supplier<TextureRegistry> registry = TextureReloaderWiring::liveRegistry;

    /**
     * The whole-pack rescan. Seam for the same reason: {@code loadModels} walks
     * the real config tree, which a unit test has no business doing.
     */
    public static Runnable packReloader = () -> CommonProxy.loadModels(false);

    private TextureReloaderWiring()
    {}

    /**
     * Fill all four {@link TextureReloader} slots. Idempotent.
     */
    public static void install()
    {
        TextureReloader.setTextureHandler(TextureReloaderWiring::reloadTexture);
        TextureReloader.setGifHandler(TextureReloaderWiring::reloadGif);
        TextureReloader.setMultiskinHandler(TextureReloaderWiring::reloadMultiskin);
        TextureReloader.setReloadAllHandler(TextureReloaderWiring::reloadAll);
    }

    /** Restore the seams to their production defaults (test teardown). */
    public static void resetSeams()
    {
        registry = TextureReloaderWiring::liveRegistry;
        packReloader = () -> CommonProxy.loadModels(false);
    }

    /* Handlers */

    /**
     * SEAM(P87) filled: close-and-re-register a plain texture in place.
     */
    public static void reloadTexture(Identifier id)
    {
        if (id == null)
        {
            return;
        }

        evict(id);

        ModelExtrudedLayer.clearByIdentifier(id);
    }

    /**
     * SEAM(P89) filled: evict the GIF decode cache plus the proxy texture and
     * all of its frame textures.
     */
    public static void reloadGif(Identifier id)
    {
        if (id == null)
        {
            return;
        }

        evictGifCache(id);
        evictPrefix(id);

        ModelExtrudedLayer.clearByIdentifier(id);
    }

    /**
     * SEAM(P91) filled: re-composite every multiskin fed by this file.
     */
    public static void reloadMultiskin(Identifier id)
    {
        if (id == null)
        {
            return;
        }

        ResourceLocation child = new ResourceLocation(id.getNamespace(), id.getPath());

        for (MultiResourceLocation multi : MultiResourceLocationManager.byChild(child))
        {
            MultiskinThread.add(multi);
        }
    }

    /**
     * SEAM(S12/P68-69) filled: the whole-pack rescan.
     */
    public static void reloadAll()
    {
        packReloader.run();
    }

    /* Texture-map surgery */

    /**
     * Drop exactly {@code id}. The shared missing-texture sentinel is evicted
     * but never closed — vanilla's own {@code registerTexture} guard, and the
     * one {@code TextureRegistry.clearDynamic} keeps.
     */
    private static void evict(Identifier id)
    {
        TextureRegistry textures = registry();

        if (textures == null)
        {
            return;
        }

        AbstractTexture missing = TextureRegistry.missingTexture();
        AbstractTexture texture = textures.map().remove(id);

        close(texture, missing, id);
    }

    /**
     * Drop {@code id} and every identifier in the <i>same namespace</i> whose
     * path starts with it — the GIF proxy plus its {@code .gif_/frameN.png}
     * children (the sanitized spelling of legacy's {@code .gif>/} grammar).
     *
     * <p>Namespace-scoped on purpose: {@code TextureRegistry.clearDynamic}
     * matches a path prefix across <em>all</em> dynamic namespaces because
     * {@code /model clear} is a user-typed blunt instrument, whereas here the
     * namespace is known exactly.</p>
     */
    private static void evictPrefix(Identifier id)
    {
        TextureRegistry textures = registry();

        if (textures == null)
        {
            return;
        }

        AbstractTexture missing = TextureRegistry.missingTexture();
        Iterator<Map.Entry<Identifier, AbstractTexture>> it = textures.map().entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<Identifier, AbstractTexture> entry = it.next();
            Identifier key = entry.getKey();

            if (key == null || !key.getNamespace().equals(id.getNamespace()) || !key.getPath().startsWith(id.getPath()))
            {
                continue;
            }

            AbstractTexture texture = entry.getValue();

            it.remove();
            close(texture, missing, key);
        }
    }

    private static void close(AbstractTexture texture, AbstractTexture missing, Identifier id)
    {
        if (texture == null || texture == missing)
        {
            return;
        }

        try
        {
            texture.close();
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to release the reloaded texture {}", id, e);
        }
    }

    /* GIF decode cache */

    /**
     * Evict {@link GifFolder}'s {@code cache}/{@code lastModified} entry for the
     * file behind {@code id}.
     *
     * <p>The cache is keyed by the <b>disk</b> path plus the {@code '>'}
     * sentinel, so the identifier is resolved back to a file through the same
     * {@link ActorsPack} folder walk that produced it. A file deleted between
     * the watch event and the reload resolves to {@code null} — the fallback
     * then evicts by key suffix, so a delete still invalidates the cache
     * instead of leaving a decoded ghost behind.</p>
     */
    private static void evictGifCache(Identifier id)
    {
        File file = null;

        try
        {
            file = ActorsPack.INSTANCE.findFile(id.getPath());
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Failed to resolve the GIF file behind {}", id, e);
        }

        if (file != null)
        {
            String key = file.getPath() + ">";

            GifFolder.cache.remove(key);
            GifFolder.lastModified.remove(key);

            return;
        }

        /* Deleted (or otherwise unresolvable) — fall back to a suffix match on
         * the relative path, which is what the key always ends with. */
        String suffix = id.getPath() + ">";

        evictBySuffix(GifFolder.cache, suffix);
        evictBySuffix(GifFolder.lastModified, suffix);
    }

    private static void evictBySuffix(Map<String, ?> cache, String suffix)
    {
        Iterator<String> it = cache.keySet().iterator();

        while (it.hasNext())
        {
            String key = it.next();

            if (key != null && key.replace(File.separatorChar, '/').toLowerCase().endsWith(suffix))
            {
                it.remove();
            }
        }
    }

    /* Seam plumbing */

    private static TextureRegistry registry()
    {
        try
        {
            return registry.get();
        }
        catch (Exception e)
        {
            Blockbuster.LOGGER.warn("Texture registry is unavailable for a live reload", e);

            return null;
        }
    }

    /**
     * The production registry — {@code null} when there is no client (headless
     * tests, dedicated server classloads).
     */
    private static TextureRegistry liveRegistry()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getTextureManager() == null)
        {
            return null;
        }

        return TextureRegistry.get();
    }
}
