package mchorse.blockbuster.client.textures;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import at.dhyan.open_imaging.GifDecoder.GifImage;
import mchorse.blockbuster.client.compat.iris.IrisPbrGifBridge;
import mchorse.blockbuster.utils.mclib.GifFolder;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

/**
 * GIF process "thread"
 *
 * Port of Blockbuster 2.7.2's {@code mchorse.blockbuster.client.textures.GifProcessThread}.
 * Responsible for turning a decoded {@link GifFolder} into a live animated
 * {@link GifTexture}: it registers one {@link GifFrameTexture} per frame plus the
 * proxy {@link GifTexture} itself into the {@link TextureManager}.
 *
 * <p><b>Despite the name there is no thread.</b> {@link #create} calls
 * {@link #run()} synchronously on the calling thread — legacy ActorsPack wrapped
 * the call in {@code addScheduledTask} so it ran on the render thread, and P231's
 * {@link SkinPipelineWiring} does the same with {@code MinecraftClient.execute}.
 * The first frame of a large GIF can therefore hitch, exactly like 1.12.2; do not
 * "improve" this without a config.</p>
 *
 * <p>Legacy performed raw map surgery via {@code ReflectionUtils.getTextures};
 * here the same map is driven through the P87 {@link TextureRegistry}, whose
 * {@code register} closes the replaced texture (legacy {@code deleteGlTexture()})
 * and leaves the shared missing sprite alone.</p>
 *
 * <p>Both GL-touching steps sit behind {@code static} seams ({@link #registry},
 * {@link #frameFactory}) so {@code GifProcessPipelineTest} can drive the whole
 * (re)registration pipeline over a plain map with counting stub textures — no
 * client, no LWJGL natives.</p>
 */
public class GifProcessThread implements Runnable
{
    public static final Map<Identifier, GifProcessThread> THREADS = new HashMap<Identifier, GifProcessThread>();

    /**
     * SEAM: the texture registry {@link #run()} registers into. Defaults to the
     * live P87 registry over {@code TextureManager}'s own map.
     */
    public static Supplier<TextureRegistry> registry = TextureRegistry::get;

    /**
     * SEAM: builds the GL-backed texture for one decoded frame. Defaults to
     * {@link GifFrameTexture}, which uploads through {@code NativeImage} and so
     * needs LWJGL natives.
     */
    public static FrameTextureFactory frameFactory = GifFrameTexture::new;

    public Identifier texture;
    public GifFolder gifFile;

    @FunctionalInterface
    public interface FrameTextureFactory
    {
        AbstractTexture create(GifFolder gif, int index);
    }

    public GifProcessThread(Identifier texture, GifFolder gif)
    {
        this.texture = texture;
        this.gifFile = gif;
    }

    @Override
    public void run()
    {
        try
        {
            TextureRegistry textures = registry.get();

            GifImage image = this.gifFile.gif;
            int[] delays = new int[image.getFrameCount()];
            Identifier[] frames = new Identifier[delays.length];

            for (int i = 0; i < delays.length; i++)
            {
                delays[i] = image.getDelay(i);
                frames[i] = frameLocation(this.texture, i);

                AbstractTexture frame = frameFactory.create(this.gifFile, i);

                /* register replaces and closes any stale frame texture. */
                textures.register(frames[i], frame);

                /* P217.1: give Iris the gl-id -> frame mapping up front, so the
                 * first PBR lookup of a never-yet-bound frame hits. A no-op
                 * (and, importantly, does not force gl-id allocation) when Iris
                 * is absent. */
                if (frame instanceof GifFrameTexture)
                {
                    IrisPbrGifBridge.trackFrame((GifFrameTexture) frame);
                }
            }

            GifTexture texture = new GifTexture(this.texture, delays, frames);

            textures.register(this.texture, texture);

            texture.calculateDuration();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Build the identifier for frame {@code i} of {@code texture}, preserving the
     * legacy {@code <path>>/frameN.png} grammar. The intermediate mclib
     * {@link ResourceLocation} carries the {@code '>'} sentinel and its
     * {@link ResourceLocation#toIdentifier()} performs the sanitization required
     * to make the path a legal {@link Identifier}.
     *
     * <p>That sanitizer is exactly the mapping
     * {@code ActorsPack.unsanitizeGifPath} inverts, so a model storing the legacy
     * {@code anim.gif>/frame3.png} spelling and a frame registered here land on
     * the same key.</p>
     */
    public static Identifier frameLocation(Identifier texture, int i)
    {
        return new ResourceLocation(texture.getNamespace(), texture.getPath() + ">/frame" + i + ".png").toIdentifier();
    }

    public static void create(Identifier location, GifFolder gif)
    {
        GifProcessThread thread = new GifProcessThread(location, gif);

        THREADS.put(location, thread);
        thread.run();
        THREADS.remove(location);
    }
}
