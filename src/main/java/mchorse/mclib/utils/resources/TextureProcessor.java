package mchorse.mclib.utils.resources;

import mchorse.mclib.events.McLibEvents;
import mchorse.mclib.events.MultiskinProcessedEvent;
import mchorse.mclib.utils.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * CPU-side multiskin compositing (pure {@link BufferedImage}/{@link ImageIO},
 * headless-usable — the legacy class was client-only because it loaded images
 * through Minecraft's resource manager; that lookup is now behind the
 * pluggable {@link #streamProvider} seam)
 */
public class TextureProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger("mclib");

    public static Pixels pixels = new Pixels();
    public static Pixels target = new Pixels();

    /**
     * Pluggable image-stream provider for multiskin children.
     *
     * <p>Legacy resolved every child through
     * {@code Minecraft.getMinecraft().getResourceManager().getResource(child.path)},
     * i.e. through the <b>whole</b> pack stack plus McLib's/Blockbuster's own
     * resource-manager branches. The production implementation is
     * {@code Textures.multiskinStream} (S7/P91, wired in
     * {@code Textures.installSeams()}); this declaration default is only the
     * <b>headless fallback</b>: a classpath {@code /assets/<domain>/<path>}
     * lookup, then a plain filesystem path. Tests inject in-memory PNGs through
     * this seam.</p>
     *
     * <p><b>S22 batch W-C.</b> Until P268 this field kept the classpath fallback
     * in game as well, so a child that lived in a resource pack, in the
     * {@code b.a} skins folders, behind an {@code http}/{@code https} URL, or in
     * a nested composite resolved off the mod/Minecraft jars and simply did not
     * composite (it became a transparent hole in the finished skin). The seam
     * was invisible to {@code SeamWiringGuardTest} because it is assigned at its
     * declaration — a real-but-wrong default is a fourth shape of dead wiring,
     * and the guard only asks whether a field is assigned at all.</p>
     */
    public static IStreamProvider streamProvider = TextureProcessor::defaultStream;

    /**
     * Render-thread dispatch seam for the {@code MultiskinProcessedEvent} (S7/P91).
     *
     * <p>Legacy {@code TextureProcessor.postProcess} marshalled the event onto the
     * MC main thread via {@code Minecraft.getMinecraft().addScheduledTask} — because
     * the documented consumer, {@code ModelExtrudedLayer.forceReload(location, image)}
     * (P79), regenerates GL texture layers and must run on the render thread.
     * In multithreaded multiskin mode ({@code McLib.multiskinMultiThreaded}, default
     * {@code true}) {@code postProcess} is invoked from {@code MultiskinThread.run()}
     * on the background worker thread, so firing the event inline would run the GL
     * consumer off the render thread. This seam re-marshals it: the client installs
     * a {@code MinecraftClient.getInstance()::execute} dispatcher (P91, in
     * {@code Textures.init}); the default runs inline so headless tests observe the
     * event synchronously and the shared source set stays free of client classes.</p>
     */
    public static Consumer<Runnable> renderThreadExecutor = Runnable::run;

    public static BufferedImage postProcess(MultiResourceLocation multi)
    {
        BufferedImage image = process(multi);

        /* Legacy posted MultiskinProcessedEvent on the MC main thread via
         * addScheduledTask after compositing (S7/P91). In multithreaded mode
         * postProcess runs on the MultiskinThread worker, so the event is
         * re-marshalled onto the render thread through renderThreadExecutor
         * (the GL consumer, ModelExtrudedLayer.forceReload / P79, must run
         * there). The default inline executor preserves legacy sync-path
         * behavior and keeps headless dispatch synchronous. */
        renderThreadExecutor.accept(() ->
            McLibEvents.MULTISKIN_PROCESSED.invoker().accept(
                new MultiskinProcessedEvent(multi, image)));

        return image;
    }

    public static BufferedImage process(MultiResourceLocation multi)
    {
        List<BufferedImage> images = new ArrayList<BufferedImage>();

        int w = 0;
        int h = 0;

        for (int i = 0; i < multi.children.size(); i++)
        {
            FilteredResourceLocation child = multi.children.get(i);
            BufferedImage image = null;

            try
            {
                InputStream stream = streamProvider.getStream(child.path);

                image = ImageIO.read(stream);

                w = Math.max(w, child.getWidth(image.getWidth()));
                h = Math.max(h, child.getHeight(image.getHeight()));
            }
            catch (Exception e)
            {
                /* Legacy printed the stack trace and appended a null, which the
                 * draw loop below skips — the S07 "total reader" placeholder for
                 * a multiskin child is the *skipped layer* (a transparent hole,
                 * not a black region). Kept, but as a named warning: a hole and
                 * a black skin are the two failure modes of the same missing
                 * file through different loaders, and triage needs to know which
                 * child went missing (S22 batch W-C). */
                LOGGER.warn("Multiskin child {} could not be resolved; the layer is skipped", child.path, e);
            }

            images.add(image);
        }

        if (w <= 0 || h <= 0)
        {
            /* Every child failed. Legacy ran straight into
             * new BufferedImage(0, 0, …) → IllegalArgumentException, which the
             * MultiskinThread worker swallowed (nothing uploaded, the skin stayed
             * on the 1x1 placeholder) but which propagated out of the
             * synchronous RLUtils.getStreamForMultiskin path as an *unchecked*
             * exception past SimpleTexture's IOException catch. The port is a
             * total reader "even where legacy would have crashed" (S07): the
             * composite degrades to the 1x1 transparent multiskin placeholder,
             * which is what legacy's surviving path left on screen anyway. */
            LOGGER.warn("Multiskin {} resolved no children at all; compositing the 1x1 placeholder instead", multi);

            w = Math.max(w, 1);
            h = Math.max(h, 1);
        }

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();

        for (int i = 0; i < multi.children.size(); i++)
        {
            BufferedImage child = images.get(i);

            if (child == null)
            {
                continue;
            }

            FilteredResourceLocation filter = multi.children.get(i);
            int iw = child.getWidth();
            int ih = child.getHeight();

            if (filter.scaleToLargest)
            {
                iw = w;
                ih = h;
            }
            else if (filter.scale != 0 && filter.scale > 0)
            {
                iw = (int) (iw * filter.scale);
                ih = (int) (ih * filter.scale);
            }

            if (iw > 0 && ih > 0)
            {
                if (filter.erase)
                {
                    processErase(image, child, filter, iw, ih);
                }
                else
                {
                    /* Legacy quirk (load-bearing): compares against 0xffffff
                     * (no alpha) instead of DEFAULT_COLOR, so the multiply
                     * pass runs even for the default white — harmlessly,
                     * since multiplying by white is an identity */
                    if (filter.color != 0xffffff || filter.pixelate > 1)
                    {
                        processImage(child, filter);
                    }

                    g.drawImage(child, filter.shiftX, filter.shiftY, iw, ih, null);
                }
            }
        }

        g.dispose();

        return image;
    }

    /**
     * Apply erasing
     */
    private static void processErase(BufferedImage image, BufferedImage child, FilteredResourceLocation filter, int iw, int ih)
    {
        BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        Graphics g2 = mask.getGraphics();

        g2.drawImage(child, filter.shiftX, filter.shiftY, iw, ih, null);
        g2.dispose();

        target.set(mask);
        pixels.set(image);

        for (int p = 0, c = target.getCount(); p < c; p++)
        {
            Color pixel = target.getColor(p);

            if (pixel.a > 0.999F)
            {
                pixel = pixels.getColor(p);
                pixel.a = 0;
                pixels.setColor(p, pixel);
            }
        }
    }

    /**
     * Apply filters
     */
    private static void processImage(BufferedImage child, FilteredResourceLocation frl)
    {
        pixels.set(child);

        Color filter = new Color().set(frl.color);
        Color pixel = new Color();

        for (int i = 0, c = pixels.getCount(); i < c; i++)
        {
            pixel.copy(pixels.getColor(i));

            if (pixels.hasAlpha())
            {
                if (pixel.a <= 0)
                {
                    continue;
                }
            }

            if (frl.pixelate > 1)
            {
                int x = pixels.toX(i);
                int y = pixels.toY(i);
                boolean origin = x % frl.pixelate == 0 && y % frl.pixelate == 0;

                x -= x % frl.pixelate;
                y -= y % frl.pixelate;

                pixel.copy(pixels.getColor(x, y));
                pixels.setColor(i, pixel);

                if (!origin)
                {
                    continue;
                }
            }

            pixel.r *= filter.r;
            pixel.g *= filter.g;
            pixel.b *= filter.b;
            pixel.a *= filter.a;
            pixels.setColor(i, pixel);
        }
    }

    /**
     * The headless fallback behind {@link #streamProvider}: classpath
     * {@code /assets/<domain>/<path>}, then a plain filesystem path. In game the
     * seam is replaced by {@code Textures.multiskinStream} (P268), which is what
     * legacy actually did; this remains for the server/headless case, where
     * there is no {@code MinecraftClient} and therefore no resource manager.
     *
     * <p><b>P252.</b> This is the one place a user-supplied texture path reaches
     * a loader <i>without</i> passing {@link ResourceLocation#toIdentifier()} —
     * a multiskin child is resolved to an image stream, not to an
     * {@code Identifier} — so the pre-flattening redirect has to be applied
     * here explicitly. A 1.12.2 multiskin whose base layer is
     * {@code minecraft:textures/blocks/…} would otherwise miss the classpath
     * lookup, fall through to {@code FileInputStream}, throw, and be composited
     * as a <b>hole</b> in the finished skin (the layer is caught and added as
     * {@code null}). The resource-manager provider gets the same redirect for
     * free, because it resolves through {@code toIdentifier()}.
     *
     * <p>Only the lookup is redirected; {@code child.path} keeps its legacy
     * string, so the multiskin still serializes byte for byte.</p>
     */
    public static InputStream defaultStream(ResourceLocation location) throws IOException
    {
        String domain = location.getResourceDomain();
        String path = VanillaTextureMoves.translate(domain, location.getResourcePath());

        InputStream stream = TextureProcessor.class.getResourceAsStream("/assets/" + domain + "/" + path);

        if (stream != null)
        {
            return stream;
        }

        return new FileInputStream(location.getResourcePath());
    }

    /**
     * Seam for resolving a child location to an image stream (see
     * {@link #streamProvider})
     */
    public interface IStreamProvider
    {
        public InputStream getStream(ResourceLocation location) throws IOException;
    }
}
