package mchorse.mclib.utils.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import mchorse.mclib.McLib;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * {@link ResourceLocation} utility methods
 *
 * This class has utils for saving and reading {@link ResourceLocation} from
 * actor model and skin.
 */
public class RLUtils
{
    private static List<IResourceTransformer> transformers = new ArrayList<IResourceTransformer>();
    private static ResourceLocation pixel = new ResourceLocation("mclib:textures/pixel.png");

    /**
     * Get stream for multi resource location (roadmap P91)
     *
     * <p>Faithful port of legacy {@code RLUtils.getStreamForMultiskin}: an
     * empty multiskin throws {@code IOException("Multi-skin is empty!")}; when
     * {@code McLib.multiskinMultiThreaded} is on, the location is queued on
     * {@link MultiskinThread} and the 1&times;1 placeholder
     * ({@code mclib:textures/pixel.png}) is returned immediately (the visible
     * "placeholder flash" is intentional legacy behavior); otherwise the
     * background thread is cleared and the composite is produced synchronously.</p>
     *
     * <p>Legacy returned an {@code IResource}; the port returns the resolved
     * {@link InputStream} directly (the P88 {@code getResource} mixin wraps it
     * in a 1.20.4 {@code Resource}).</p>
     */
    public static InputStream getStreamForMultiskin(MultiResourceLocation multi) throws IOException
    {
        if (multi.children.isEmpty())
        {
            throw new IOException("Multi-skin is empty!");
        }

        if (McLib.multiskinMultiThreaded.get())
        {
            MultiskinThread.add(multi);

            return placeholderStream();
        }

        MultiskinThread.clear();

        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();

            ImageIO.write(TextureProcessor.postProcess(multi), "png", stream);

            return new ByteArrayInputStream(stream.toByteArray());
        }
        catch (IOException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    /**
     * The {@code mclib:textures/pixel.png} placeholder the multithreaded path
     * shows until the real composite uploads. Reads the bundled asset, falling
     * back to a generated 1&times;1 transparent PNG so the path is total even
     * when the classpath asset is missing.
     */
    private static InputStream placeholderStream() throws IOException
    {
        InputStream stream = RLUtils.class.getResourceAsStream("/assets/mclib/textures/pixel.png");

        if (stream != null)
        {
            return stream;
        }

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ImageIO.write(image, "png", out);

        return new ByteArrayInputStream(out.toByteArray());
    }

    public static void register(IResourceTransformer transformer)
    {
        transformers.add(transformer);
    }

    public static ResourceLocation create(String path)
    {
        for (IResourceTransformer transformer : transformers)
        {
            path = transformer.transform(path);
        }

        return new TextureLocation(path);
    }

    public static ResourceLocation create(String domain, String path)
    {
        for (IResourceTransformer transformer : transformers)
        {
            String newDomain = transformer.transformDomain(domain, path);
            String newPath = transformer.transformPath(domain, path);

            domain = newDomain;
            path = newPath;
        }

        return new TextureLocation(domain, path);
    }

    public static ResourceLocation create(NbtElement base)
    {
        ResourceLocation location = MultiResourceLocation.from(base);

        if (location != null)
        {
            return location;
        }

        if (base instanceof NbtString)
        {
            return create(base.asString());
        }

        return null;
    }

    public static ResourceLocation create(JsonElement element)
    {
        ResourceLocation location = MultiResourceLocation.from(element);

        if (location != null)
        {
            return location;
        }

        if (element.isJsonPrimitive())
        {
            return create(element.getAsString());
        }

        return null;
    }

    public static NbtElement writeNbt(ResourceLocation location)
    {
        if (location instanceof IWritableLocation)
        {
            return ((IWritableLocation) location).writeNbt();
        }
        else if (location != null)
        {
            return NbtString.of(location.toString());
        }

        return null;
    }

    public static JsonElement writeJson(ResourceLocation location)
    {
        if (location instanceof IWritableLocation)
        {
            return ((IWritableLocation) location).writeJson();
        }
        else if (location != null)
        {
            return new JsonPrimitive(location.toString());
        }

        return JsonNull.INSTANCE;
    }

    public static ResourceLocation clone(ResourceLocation location)
    {
        if (location instanceof IWritableLocation)
        {
            Object copy = ((IWritableLocation) location).copy();

            if (copy instanceof ResourceLocation)
            {
                return (ResourceLocation) copy;
            }
        }

        if (location != null)
        {
            return create(location.toString());
        }

        return null;
    }
}
