package mchorse.blockbuster.utils.mclib;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GIF frame pseudo-file
 *
 * Direct port of Blockbuster 2.7.2's {@code mchorse.blockbuster.utils.mclib.GifFrameFile}.
 * Represents a single {@code frameN.png} entry inside a {@link GifFolder}. The
 * super-path is truncated at the {@code .gif} boundary so the underlying
 * {@link File} still points at the real gif on disk, while {@link #getName()}
 * reports the synthetic {@code frameN.png} name and {@link #getParentFile()}
 * returns the owning {@link GifFolder}.
 *
 * <p>An out-of-range or unmatched index yields {@code index == -1} and
 * {@link #exists()} {@code == false} (total-reader contract, never a crash).</p>
 */
public class GifFrameFile extends File
{
    private static final long serialVersionUID = -3183927604124452726L;
    private static final Pattern match = Pattern.compile("\\.gif>\\/frame(\\d+)\\.png$");

    public GifFolder parent;
    public int index;

    public GifFrameFile(String pathname)
    {
        super(pathname.substring(0, pathname.indexOf(".gif>/") + 4));

        this.init(pathname);
    }

    public GifFrameFile(File parent, String child)
    {
        super(parent, child.substring(0, child.indexOf(".gif>/") + 4));

        this.init(child);
    }

    private void init(String pathname)
    {
        this.parent = new GifFolder(super.getPath());
        this.index = -1;

        if (this.parent.exists())
        {
            Matcher matcher = match.matcher(pathname);

            if (matcher.find())
            {
                int index = Integer.parseInt(matcher.group(1));

                if (index < this.parent.gif.getFrameCount())
                {
                    this.index = index;
                }
            }
        }
    }

    @Override
    public String getName()
    {
        return "frame" + this.index + ".png";
    }

    @Override
    public String getParent()
    {
        return super.getPath();
    }

    @Override
    public File getParentFile()
    {
        return this.parent;
    }

    @Override
    public boolean isDirectory()
    {
        return false;
    }

    @Override
    public boolean isFile()
    {
        return true;
    }

    @Override
    public boolean exists()
    {
        return this.index != -1;
    }
}
