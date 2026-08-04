package mchorse.blockbuster.api.resource;

import mchorse.blockbuster.Blockbuster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Classpath backed resource entry.
 *
 * <p>Ported from Blockbuster 2.7.2. The legacy anchor
 * {@code Blockbuster.class.getClassLoader()} is preserved (via the new mod's
 * {@link Blockbuster} class). Apache Commons {@code FilenameUtils.getName} and
 * {@code FileUtils.copyInputStreamToFile} are replaced with local /
 * {@link java.nio.file.Files} equivalents keeping the same semantics.</p>
 *
 * <p><b>Contract:</b> a {@code StreamEntry} with a {@code null} path is a valid
 * "absent" entry: empty name, never exists, {@code null} stream.
 * {@link #hasChanged()} is always {@code false}; {@link #lastModified()} returns
 * the fixed {@link #time}.</p>
 */
public class StreamEntry implements IResourceEntry
{
    public String path;
    public long time;
    public ClassLoader loader = Blockbuster.class.getClassLoader();

    public StreamEntry(String path, long time)
    {
        this.path = path;
        this.time = time;
    }

    @Override
    public String getName()
    {
        return this.path == null ? "" : getFileName(this.path);
    }

    public StreamEntry(String path, long time, ClassLoader loader)
    {
        this(path, time);

        this.loader = loader;
    }

    /**
     * Local replacement for Apache Commons {@code FilenameUtils.getName}:
     * returns the trailing name component, handling both {@code /} and
     * {@code \\} separators.
     */
    private static String getFileName(String path)
    {
        int index = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

        return index < 0 ? path : path.substring(index + 1);
    }

    @Override
    public InputStream getStream() throws IOException
    {
        return this.path == null ? null : this.loader.getResourceAsStream(this.path);
    }

    @Override
    public boolean exists()
    {
        return this.path != null && this.loader.getResource(this.path) != null;
    }

    @Override
    public boolean hasChanged()
    {
        return false;
    }

    @Override
    public long lastModified()
    {
        return this.time;
    }

    @Override
    public boolean copyTo(File file)
    {
        /* Mirror FileUtils.copyInputStreamToFile: create parent directories,
         * copy the stream, close it. */
        try (InputStream in = this.getStream())
        {
            /* P284: an absent entry (null path, or a jar resource that is not
             * there — both legal per this class's contract) used to reach
             * Files.newOutputStream first, which TRUNCATES the destination, and
             * only then NPE on in.read. The NPE was swallowed below and the
             * method returned false, leaving the user's existing model.obj /
             * .vox / skin at zero bytes. Nothing to copy means nothing to
             * write. */
            if (in == null)
            {
                return false;
            }

            File parent = file.getParentFile();

            if (parent != null)
            {
                Files.createDirectories(parent.toPath());
            }

            try (OutputStream out = Files.newOutputStream(file.toPath()))
            {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = in.read(buffer)) != -1)
                {
                    out.write(buffer, 0, read);
                }
            }

            return true;
        }
        catch (IOException e)
        {}
        catch (NullPointerException e)
        {}

        return false;
    }
}
