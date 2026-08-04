package mchorse.blockbuster.utils.mclib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.dhyan.open_imaging.GifDecoder;
import at.dhyan.open_imaging.GifDecoder.GifImage;

/**
 * GIF pseudo-folder
 *
 * Direct port of Blockbuster 2.7.2's {@code mchorse.blockbuster.utils.mclib.GifFolder}
 * (pure {@link File} subclass, no Minecraft dependencies). A {@code .gif} file is
 * presented to the resource system as a <i>directory</i> whose children are the
 * decoded {@code frameN.png} entries, so pickers and the texture pipeline can treat
 * animation frames as ordinary files.
 *
 * <h3>Legacy quirks preserved (parity, see plan/S07-textures-skins.md P89)</h3>
 * <ul>
 *   <li>{@link #getName()}/{@link #getPath()} append the {@code '>'} virtual-folder
 *   sentinel; this suffix is a <b>format contract</b> that is persisted verbatim
 *   inside models and morphs (e.g. {@code b.a:model/skins/anim.gif>/frame4.png}).</li>
 *   <li><b>Inverted staleness check</b>: the cache is reused when
 *   {@code last <= this.lastModified()} — i.e. even when the file on disk is
 *   <i>newer</i> than the cache stamp. This is backwards versus
 *   {@code AudioFile.canBeUpdated} but is kept for parity: edited GIFs never
 *   hot-reload within a session, only a failed decode ({@link IOException})
 *   evicts. The manual-reload escape hatch lives in P92.</li>
 * </ul>
 */
public class GifFolder extends File
{
    public static Map<String, Long> lastModified = new HashMap<String, Long>();
    public static Map<String, GifImage> cache = new HashMap<String, GifImage>();

    private static final long serialVersionUID = 3058345951609134509L;

    public GifImage gif;

    public GifFolder(String pathname)
    {
        super(pathname);

        String path = this.getPath();
        Long last = lastModified.get(path);

        if (last != null && last <= this.lastModified())
        {
            this.gif = cache.get(path);
        }
        else
        {
            try
            {
                InputStream in = new FileInputStream(pathname);

                this.gif = GifDecoder.read(in);

                in.close();
                cache.put(path, this.gif);
                lastModified.put(path, this.lastModified());
            }
            catch (IOException e)
            {
                this.gif = null;
                cache.remove(path);
                lastModified.remove(path);
            }
        }
    }

    @Override
    public String getName()
    {
        return super.getName() + ">";
    }

    @Override
    public String getPath()
    {
        return super.getPath() + ">";
    }

    public String getFilePath()
    {
        return super.getPath();
    }

    @Override
    public boolean isDirectory()
    {
        return true;
    }

    @Override
    public boolean isFile()
    {
        return false;
    }

    @Override
    public File[] listFiles()
    {
        List<File> list = new ArrayList<File>();

        for (int i = 0; i < this.gif.getFrameCount(); i++)
        {
            list.add(new GifFrameFile(this.getPath() + "/frame" + i + ".png"));
        }

        return list.toArray(new File[0]);
    }

    @Override
    public boolean exists()
    {
        return this.gif != null;
    }
}
