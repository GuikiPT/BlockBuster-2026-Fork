package mchorse.mclib.utils.resources;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * The multiskin half of the resource-manager seam (roadmap P91 / P249, defect
 * F).
 *
 * <p>1.12.2's mclib ASM coremod patched
 * {@code SimpleReloadableResourceManager.getResource} to branch on
 * {@code instanceof MultiResourceLocation} and hand the location to
 * {@code RLUtils.getStreamForMultiskin}. 1.20.4's {@code Identifier} is final
 * and carries no such type information, so
 * {@link MultiResourceLocation#toIdentifier()} encodes the composite's
 * {@link MultiResourceLocationManager} id into the path
 * ({@code mclib:multiskin/<id>}) and this class is the branch that decodes it.
 * The actual interception lives in Blockbuster's
 * {@code ReloadableResourceManagerImplMixin}, alongside the {@code b.a} / URL
 * branches — one mixin, three namespaces, exactly as the P88 plan intended.</p>
 *
 * <p>Kept in {@code src/main} and free of client/GL imports so the branch logic
 * is headless-testable.</p>
 */
public class MultiskinResources
{
    /** Whether {@code namespace:path} names a multiskin composite. */
    public static boolean handles(String namespace, String path)
    {
        return MultiResourceLocation.NAMESPACE.equals(namespace)
            && path != null && path.startsWith(MultiResourceLocation.PREFIX);
    }

    /**
     * The composite named by {@code namespace:path}, or {@code null} when the
     * path is not a multiskin path, the id doesn't parse, or nothing is
     * registered under it (total — never throws).
     */
    public static MultiResourceLocation resolve(String namespace, String path)
    {
        if (!handles(namespace, path))
        {
            return null;
        }

        try
        {
            return MultiResourceLocationManager.byId(
                Integer.parseInt(path.substring(MultiResourceLocation.PREFIX.length())));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Composite (or, in multithreaded mode, the placeholder + a queued job) for
     * {@code namespace:path}.
     *
     * @throws FileNotFoundException when nothing is registered under the id —
     *         the resource manager treats that exactly like a missing file
     */
    public static InputStream open(String namespace, String path) throws IOException
    {
        MultiResourceLocation multi = resolve(namespace, path);

        if (multi == null)
        {
            throw new FileNotFoundException(namespace + ":" + path);
        }

        return RLUtils.getStreamForMultiskin(multi);
    }
}
