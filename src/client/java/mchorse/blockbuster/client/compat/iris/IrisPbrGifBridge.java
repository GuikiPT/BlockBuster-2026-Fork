package mchorse.blockbuster.client.compat.iris;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import mchorse.blockbuster.client.textures.GifFrameTexture;
import mchorse.blockbuster.client.textures.GifTexture;
import mchorse.blockbuster.client.textures.VoxPaletteTexture;
import net.minecraft.client.texture.AbstractTexture;

/**
 * Serves animated GIF skins' normal/specular companion maps to Iris (P217.1),
 * and VOX limb palettes' (P217.3).
 *
 * <p>Legacy did this by reflecting Optifine: {@code Config.isShaders()} gated
 * the work, {@code ShadersTex.getMultiTexID}/{@code setupTexture} uploaded one
 * GL texture holding base + normal + specular stacked in a single {@code int[]},
 * and {@code GifTexture.updateMultiTex} copied the resulting handle from the
 * frame object onto the wrapper object so whichever got bound looked valid.</p>
 *
 * <p>None of that mechanism survives, and none of it needs to:</p>
 *
 * <ul>
 *   <li><b>The gate is implicit.</b> Iris only asks a {@code PBRTextureLoader}
 *       for maps while a PBR-capable pack is in use, so nothing is packed or
 *       uploaded on a vanilla install — the modern {@code Config.isShaders()}.
 *       {@link GifFrameTexture#getPbrTexture} is lazy for the same reason.</li>
 *   <li><b>The packing is not the contract.</b> Iris wants two separate
 *       textures; the legacy triple is kept as {@link PbrFramePacker}'s output
 *       because it is what the goldens pin, and sliced at upload.</li>
 *   <li><b>{@code multiTex} field-copying has no analogue.</b> Iris keys off the
 *       GL texture id, which the wrapper and the frame already share — see
 *       {@link GifPbrTexture} for the full argument.</li>
 * </ul>
 *
 * <h2>Reflection, again</h2>
 *
 * <p>Like {@link IrisCompat}, this class binds Iris by reflection and adds no
 * Gradle dependency (S21 open question 9, decided in P217). The two types it
 * needs — {@code PBRTextureLoaderRegistry} and {@code TextureTracker} — are Iris
 * <i>internals</i>, not the v0 API, which makes the "degrade instead of
 * {@code NoSuchMethodError}" argument stronger here than it was for P217, not
 * weaker: a moved internal simply leaves {@link #isInstalled()} false and the
 * skin renders with Iris' default flat maps. The loader itself is a
 * {@link Proxy} over Iris' {@code PBRTextureLoader} interface, which is possible
 * precisely because everything crossing the boundary
 * ({@code AbstractTexture}, {@code ResourceManager}) is vanilla.</p>
 *
 * <h2>The VOX arm (P217.3)</h2>
 *
 * <p>The second legacy Optifine texture route lands in the same place, because
 * the registry keys on the texture class and nothing else. A VOX limb's palette
 * strip ({@link VoxPaletteTexture}) gets a loader that serves a flat normal plus
 * a specular map filled with the limb's colour — the two thirds Optifine's
 * patched {@code DynamicTexture} used to carry inside the palette's own
 * {@code int[]}. The palette image itself is untouched; {@link VoxPbrPacker}
 * carries the full derivation, including why raising
 * {@code VoxTexture.ROWS_SHADER} would be a regression rather than the port.
 * (S21 open question 10, resolved.)</p>
 *
 * <p><b>P218 note.</b> Shader-bound curves need far more Iris internals than
 * two singletons and cannot reasonably be reflected; that is the phase where
 * {@code modCompileOnly} earns its cost. If it lands, this class should be
 * rewritten against the real types — the {@link Sink} seam is the join.</p>
 */
public final class IrisPbrGifBridge
{
    public static final String REGISTRY_CLASS = "net.irisshaders.iris.texture.pbr.loader.PBRTextureLoaderRegistry";
    public static final String LOADER_CLASS = "net.irisshaders.iris.texture.pbr.loader.PBRTextureLoader";
    public static final String CONSUMER_CLASS = LOADER_CLASS + "$PBRTextureConsumer";
    public static final String TRACKER_CLASS = "net.irisshaders.iris.texture.TextureTracker";

    /**
     * Everything this bridge does to Iris, behind one interface — so the
     * with-Iris shape is exercisable headlessly and so a future
     * compile-against-Iris implementation can drop in without touching the
     * texture stack.
     */
    public interface Sink
    {
        /**
         * Iris' {@code PBRTextureLoaderRegistry.INSTANCE.register(type, loader)}.
         *
         * @return whether the registration took.
         */
        boolean registerLoader(Class<? extends AbstractTexture> type);

        /** Iris' {@code TextureTracker.INSTANCE.trackTexture(glId, texture)}. */
        void trackTexture(int glId, AbstractTexture texture);
    }

    /** Test seam. When non-null it replaces the whole reflective path. */
    public static Sink sink;

    private static boolean setupDone;
    private static boolean installed;

    /* One-shot reflective handles (legacy ReflectionElement semantics). */
    private static Method registerMethod;
    private static Object registryInstance;
    private static Object trackerInstance;
    private static Method trackMethod;
    private static Method acceptNormal;
    private static Method acceptSpecular;

    private IrisPbrGifBridge()
    {}

    /**
     * Register the GIF PBR loader with Iris. Idempotent and one-shot: a failure
     * is cached, exactly like every other probe in this package.
     *
     * <p>Called from {@code BlockbusterClient.onInitializeClient}. Without Iris
     * this returns {@code false} having touched nothing at all — no
     * {@code Class.forName}, no change to the S7 texture pipeline.</p>
     *
     * @return whether the loader is now installed.
     */
    public static boolean setup()
    {
        if (setupDone)
        {
            return installed;
        }

        setupDone = true;

        if (sink != null)
        {
            /* Non-short-circuit on purpose: every class is offered exactly once
             * per JVM, whatever the registry answers to the one before it. */
            installed = sink.registerLoader(GifFrameTexture.class)
                & sink.registerLoader(GifTexture.class)
                & sink.registerLoader(VoxPaletteTexture.class);

            return installed;
        }

        if (!IrisCompat.isLoaded())
        {
            return false;
        }

        try
        {
            Class<?> registry = Class.forName(REGISTRY_CLASS);
            Class<?> loader = Class.forName(LOADER_CLASS);
            Class<?> consumer = Class.forName(CONSUMER_CLASS);

            acceptNormal = consumer.getMethod("acceptNormalTexture", AbstractTexture.class);
            acceptSpecular = consumer.getMethod("acceptSpecularTexture", AbstractTexture.class);

            registryInstance = registry.getField("INSTANCE").get(null);
            registerMethod = registry.getMethod("register", Class.class, loader);

            Object proxy = Proxy.newProxyInstance(loader.getClassLoader(), new Class<?>[] {loader}, handler());

            registerMethod.invoke(registryInstance, GifFrameTexture.class, proxy);
            registerMethod.invoke(registryInstance, GifTexture.class, proxy);
            registerMethod.invoke(registryInstance, VoxPaletteTexture.class, proxy);

            installed = true;
        }
        catch (Throwable t)
        {
            /* Swallow, exactly like the legacy helpers: a moved Iris internal
             * costs the PBR maps, never the frame. */
            installed = false;
        }

        return installed;
    }

    /** Whether the PBR loader was successfully registered with Iris. */
    public static boolean isInstalled()
    {
        return installed;
    }

    /**
     * Tell Iris which texture object owns a freshly created frame's GL id.
     *
     * <p>Iris populates its {@code TextureTracker} from bind calls, so a frame
     * that has never been bound has no entry and its first PBR lookup misses.
     * Registering the mapping at creation time closes that window — the same
     * reason BBS calls {@code TextureTracker.INSTANCE.trackTexture} explicitly
     * for its own textures.</p>
     *
     * <p><b>Skipped entirely without Iris.</b> That is not just an
     * optimisation: {@code getGlId()} allocates the GL object on first call, so
     * probing it here would move allocation earlier for every vanilla user. The
     * no-Iris path must leave the S7 pipeline byte-for-byte as it was.</p>
     */
    public static void trackFrame(GifFrameTexture frame)
    {
        if (frame == null || !installed)
        {
            return;
        }

        /* getGlId() allocates the GL object on first call - which is exactly why
         * the `installed` guard comes first and is never reordered. */
        track(frame.getGlId(), frame);
    }

    /**
     * Tell Iris which texture object owns a freshly uploaded VOX palette's GL id
     * (P217.3) — the {@link #trackFrame} argument, for the other texture route.
     *
     * <p>Guarded on {@link #isInstalled()} first for the same reason: without
     * Iris this method must not so much as call {@code getGlId()}, so a vanilla
     * install's VOX upload path is byte-for-byte what S7/P91.1 landed.</p>
     */
    public static void trackPalette(VoxPaletteTexture palette)
    {
        if (palette == null || !installed)
        {
            return;
        }

        track(palette.getGlId(), palette);
    }

    /**
     * Push one {@code glId -> texture} mapping into Iris' {@code TextureTracker}.
     * Silent no-op unless the PBR loader is installed, so nothing reaches Iris
     * on an install where the bridge never took.
     */
    public static void track(int glId, AbstractTexture texture)
    {
        if (!installed)
        {
            return;
        }

        try
        {
            if (sink != null)
            {
                sink.trackTexture(glId, texture);

                return;
            }

            if (trackMethod == null)
            {
                Class<?> tracker = Class.forName(TRACKER_CLASS);

                trackerInstance = tracker.getField("INSTANCE").get(null);
                trackMethod = tracker.getMethod("trackTexture", int.class, AbstractTexture.class);
            }

            trackMethod.invoke(trackerInstance, glId, texture);
        }
        catch (Throwable t)
        {}
    }

    /**
     * The {@code PBRTextureLoader} body:
     * {@code load(T texture, ResourceManager manager, PBRTextureConsumer consumer)}.
     * Handing the consumer nothing (rather than a broken texture) is the
     * totality rule — a corrupt {@code _n.gif} must cost the map, not the skin.
     */
    private static InvocationHandler handler()
    {
        return (proxy, method, args) ->
        {
            if (!"load".equals(method.getName()) || args == null || args.length != 3)
            {
                return objectMethod(proxy, method, args);
            }

            try
            {
                AbstractTexture normal = pbrTextureFor(args[0], PbrFramePacker.NORMAL);
                AbstractTexture specular = pbrTextureFor(args[0], PbrFramePacker.SPECULAR);

                if (normal != null)
                {
                    acceptNormal.invoke(args[2], normal);
                }

                if (specular != null)
                {
                    acceptSpecular.invoke(args[2], specular);
                }
            }
            catch (Throwable t)
            {}

            return null;
        };
    }

    /**
     * The loader body's dispatch: build the companion map for whichever of our
     * texture objects Iris asked about, or {@code null} for anything else (which
     * hands the consumer nothing and leaves Iris on its own defaults).
     *
     * <p>GIF skins get the late-resolving {@link GifPbrTexture} because the map
     * has to follow the animation; a VOX palette is static, so its maps are
     * handed over directly — {@link VoxPaletteTexture#getPbrTexture} builds them
     * on this very call, which is the lazy "only under a PBR pack" gate.</p>
     *
     * <p>Public rather than private so the headless suite can pin the dispatch
     * without an Iris proxy or a GL context.</p>
     */
    public static AbstractTexture pbrTextureFor(Object texture, int type)
    {
        if (texture instanceof GifTexture)
        {
            return new GifPbrTexture((GifTexture) texture, type);
        }

        if (texture instanceof GifFrameTexture)
        {
            return new GifPbrTexture((GifFrameTexture) texture, type);
        }

        if (texture instanceof VoxPaletteTexture)
        {
            return ((VoxPaletteTexture) texture).getPbrTexture(type);
        }

        return null;
    }

    /** {@link Object}'s three methods reach the handler too; answer them sanely. */
    private static Object objectMethod(Object proxy, Method method, Object[] args)
    {
        String name = method.getName();

        if ("toString".equals(name))
        {
            return "IrisPbrGifBridge$PBRTextureLoader";
        }

        if ("hashCode".equals(name))
        {
            return System.identityHashCode(proxy);
        }

        if ("equals".equals(name))
        {
            return args != null && args.length == 1 && args[0] == proxy;
        }

        return null;
    }

    /** Test-only: forget every cached lookup and seam. */
    public static void reset()
    {
        sink = null;
        setupDone = false;
        installed = false;
        registerMethod = null;
        registryInstance = null;
        trackerInstance = null;
        trackMethod = null;
        acceptNormal = null;
        acceptSpecular = null;
    }
}
