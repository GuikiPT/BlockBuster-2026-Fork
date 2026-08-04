package mchorse.mclib.utils.resources;

import java.awt.image.BufferedImage;
import java.util.Stack;

/**
 * Off-thread multiskin compositor (roadmap P91) — port of legacy
 * {@code mchorse.mclib.utils.resources.MultiskinThread}.
 *
 * <p>The legacy class was a singleton background {@link Runnable} over a
 * {@code Stack<MultiResourceLocation>} that, once a location's texture object
 * existed in the vanilla texture map, composited off-thread and uploaded on the
 * MC main thread with {@code GL_NEAREST} min/mag. The queue bookkeeping
 * ({@link #add}, {@link #clear}, {@link #addLocation} dedupe) is preserved
 * verbatim; the two client-only touch points — "is a texture object already
 * registered for this location?" and "upload the composited image on the render
 * thread" — are lifted behind the {@link ILocationReady} and {@link IUploader}
 * seams so this class stays in the shared source set and headless-testable.</p>
 *
 * <p>Port deviation (recorded): the background thread only starts once a real
 * {@link #uploader} is installed (by the client at init). Headless / server
 * side, {@link #add} enqueues and dedupes exactly as before but spins up no
 * thread — there is nothing to upload to.</p>
 */
public class MultiskinThread implements Runnable
{
    private static MultiskinThread instance;
    private static Thread thread;

    /**
     * Render-thread GL upload seam (installed client-side). Null on the server
     * / in headless tests.
     */
    public static IUploader uploader;

    /**
     * "Has something bound/registered a texture object for this location yet?"
     * seam — legacy's {@code ReflectionUtils.getTextures(mc.renderEngine)
     * .get(location) != null} poll, lifted out of the shared source set.
     *
     * <p>Installed client-side by {@code Textures.installSeams()} (S22, batch
     * V-J); see {@code Textures.isMultiskinReady} for why the previous
     * "always ready, P87 makes it true anyway" default was wrong. The
     * declaration default stays {@code true} for the server / headless case,
     * where there is no {@link #uploader} and therefore no worker to gate.</p>
     */
    public static ILocationReady ready = (location) -> true;

    public Stack<MultiResourceLocation> locations = new Stack<MultiResourceLocation>();

    public static synchronized void add(MultiResourceLocation location)
    {
        if (instance != null && (thread == null || !thread.isAlive()))
        {
            instance = null;
        }

        if (instance == null)
        {
            instance = new MultiskinThread();
            instance.addLocation(location);

            /* Only spin the worker up when there's a GL sink to upload to */
            if (uploader != null)
            {
                thread = new Thread(instance, "mclib-multiskin");
                thread.start();
            }
        }
        else
        {
            instance.addLocation(location);
        }
    }

    public static void clear()
    {
        instance = null;
    }

    public static MultiskinThread getInstance()
    {
        return instance;
    }

    public synchronized void addLocation(MultiResourceLocation location)
    {
        if (this.locations.contains(location))
        {
            return;
        }

        this.locations.add(location);
    }

    @Override
    public void run()
    {
        while (!this.locations.isEmpty() && instance != null)
        {
            MultiResourceLocation location = this.locations.peek();

            try
            {
                if (ready.isReady(location))
                {
                    this.locations.pop();

                    BufferedImage image = TextureProcessor.postProcess(location);

                    if (uploader != null)
                    {
                        uploader.upload(location, image);
                    }
                }

                Thread.sleep(100);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        instance = null;
        thread = null;
    }

    /**
     * Render-thread upload of a freshly composited multiskin. Legacy allocated
     * the texture for {@code location}'s existing GL id and uploaded with
     * {@code GL_NEAREST} min/mag.
     */
    public interface IUploader
    {
        void upload(MultiResourceLocation location, BufferedImage image);
    }

    /**
     * Whether a texture object is registered for {@code location} yet (the
     * handshake the compositor waits on).
     */
    public interface ILocationReady
    {
        boolean isReady(MultiResourceLocation location);
    }
}
