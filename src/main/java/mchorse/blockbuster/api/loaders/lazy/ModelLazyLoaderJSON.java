package mchorse.blockbuster.api.loaders.lazy;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster.api.formats.IMeshes;
import mchorse.blockbuster.api.resource.FileEntry;
import mchorse.blockbuster.api.resource.IResourceEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Lazy loader for {@code model.json} based models.
 *
 * <p>Ported from Blockbuster 2.7.2. The {@link #count()} file-presence bitmask
 * and the {@link #stillExists()} "same set of files still exists" contract are
 * load-bearing for the reload loop (adding/removing companion files triggers a
 * reload).</p>
 *
 * <p>Apache Commons {@code FileUtils} is replaced with {@link java.nio.file.Files}
 * equivalents, keeping the "swallow IOException -&gt; boolean" semantics.</p>
 */
public class ModelLazyLoaderJSON implements IModelLazyLoader
{
    public IResourceEntry model;
    public long lastTime;
    public int lastCount = -1;

    public ModelLazyLoaderJSON(IResourceEntry model)
    {
        this.model = model;
    }

    public int count()
    {
        return this.model.exists() ? 1 : 0;
    }

    @Override
    public long getLastTime()
    {
        return this.lastTime;
    }

    @Override
    public void setLastTime(long lastTime)
    {
        if (this.lastCount == -1)
        {
            this.lastCount = this.count();
        }

        this.lastTime = lastTime;
    }

    @Override
    public boolean stillExists()
    {
        return this.lastCount == this.count();
    }

    @Override
    public boolean hasChanged()
    {
        return this.model.hasChanged();
    }

    @Override
    public Model loadModel(String key) throws Exception
    {
        if (!this.model.exists())
        {
            return null;
        }

        return Model.parse(this.model.getStream());
    }

    /**
     * Produce the mesh data for the given model. Base JSON models have no
     * meshes; subclasses (OBJ/VOX) override.
     *
     * <p>Legacy this is {@code @SideOnly(Side.CLIENT) protected}. In this port
     * it is public and data-only so the mesh production can be tested
     * headlessly; the GL renderer wiring stays in S6.</p>
     */
    public Map<String, IMeshes> getMeshes(String key, Model model) throws Exception
    {
        return null;
    }

    @Override
    public boolean copyFiles(File folder)
    {
        if (this.model instanceof FileEntry)
        {
            FileEntry file = (FileEntry) this.model;

            if (!file.file.getParentFile().equals(folder))
            {
                try
                {
                    copyDirectory(new File(file.file.getParentFile(), "skins"), new File(folder, "skins"));

                    return true;
                }
                catch (IOException e)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Local replacement for Apache Commons {@code FileUtils.copyDirectory}:
     * recursively copies {@code source} into {@code destination}. Throws
     * {@link IOException} when the source directory does not exist (matching the
     * legacy behaviour, where a missing {@code skins} folder makes
     * {@link #copyFiles(File)} return {@code false}).
     */
    protected static void copyDirectory(File source, File destination) throws IOException
    {
        Path src = source.toPath();

        if (!Files.isDirectory(src))
        {
            throw new IOException("Source '" + source + "' does not exist");
        }

        Path dst = destination.toPath();

        Files.createDirectories(dst);

        try (Stream<Path> walk = Files.walk(src))
        {
            for (Path path : (Iterable<Path>) walk::iterator)
            {
                Path relative = src.relativize(path);
                Path target = dst.resolve(relative.toString());

                if (Files.isDirectory(path))
                {
                    Files.createDirectories(target);
                }
                else
                {
                    Path parent = target.getParent();

                    if (parent != null)
                    {
                        Files.createDirectories(parent);
                    }

                    try (InputStream in = Files.newInputStream(path);
                         OutputStream out = Files.newOutputStream(target))
                    {
                        byte[] buffer = new byte[8192];
                        int read;

                        while ((read = in.read(buffer)) != -1)
                        {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        }
    }
}
