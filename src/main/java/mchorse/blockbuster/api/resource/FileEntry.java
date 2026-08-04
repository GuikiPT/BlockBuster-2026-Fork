package mchorse.blockbuster.api.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * File based resource entry.
 *
 * <p>Ported from Blockbuster 2.7.2. Apache Commons {@code FileUtils.copyFile}
 * is replaced with {@link java.nio.file.Files} equivalents while preserving the
 * "swallow IOException -&gt; boolean" semantics.</p>
 *
 * <p><b>Quirk (load-bearing):</b> {@link #hasChanged()} is side-effectful: it
 * updates the stored {@link #lastModified} on every call, so calling it twice
 * returns {@code false} the second time. The reload loop in {@code ModelPack}
 * relies on single-call semantics.</p>
 */
public class FileEntry implements IResourceEntry
{
    public File file;
    public long lastModified;

    public FileEntry(File file)
    {
        this.file = file;
        this.lastModified = this.lastModified();
    }

    @Override
    public String getName()
    {
        return this.file == null ? "" : this.file.getName();
    }

    @Override
    public InputStream getStream() throws IOException
    {
        return this.file == null ? null : new FileInputStream(this.file);
    }

    @Override
    public boolean exists()
    {
        return this.file != null && this.file.exists();
    }

    @Override
    public boolean hasChanged()
    {
        long lastModified = this.lastModified();
        boolean result = lastModified > this.lastModified;

        this.lastModified = lastModified;

        return result;
    }

    @Override
    public long lastModified()
    {
        return this.file == null ? 0 : this.file.lastModified();
    }

    @Override
    public boolean copyTo(File file)
    {
        try
        {
            /* Mirror FileUtils.copyFile: create parent directories and
             * overwrite the destination. */
            File parent = file.getParentFile();

            if (parent != null)
            {
                Files.createDirectories(parent.toPath());
            }

            Files.copy(this.file.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return true;
        }
        catch (IOException e)
        {}
        catch (NullPointerException e)
        {}

        return false;
    }
}
