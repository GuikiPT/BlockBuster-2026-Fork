package mchorse.blockbuster.api.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Resource entry
 *
 * Used for the model system to allow both file based stream creation
 * and just stream creation via class loader/inside of jar.
 *
 * <p>Ported 1:1 from Blockbuster 2.7.2 (Forge 1.12.2). The
 * {@link #createEntry(Object)} dispatch and the "null path StreamEntry is a
 * valid absent entry" contract are load-bearing.</p>
 */
public interface IResourceEntry
{
    public String getName();

    public static IResourceEntry createEntry(Object object)
    {
        if (object instanceof File)
        {
            return new FileEntry((File) object);
        }
        else if (object instanceof String)
        {
            return new StreamEntry((String) object, System.currentTimeMillis());
        }

        return new StreamEntry(null, 0);
    }

    public InputStream getStream() throws IOException;

    public boolean exists();

    public boolean hasChanged();

    public long lastModified();

    public boolean copyTo(File file);
}
