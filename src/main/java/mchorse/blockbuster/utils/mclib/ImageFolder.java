package mchorse.blockbuster.utils.mclib;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Image directory wrapper
 *
 * Direct port of Blockbuster 2.7.2's {@code mchorse.blockbuster.utils.mclib.ImageFolder}.
 * Post-processes a directory listing so that every decodable {@code .gif} file is
 * listed <b>twice</b> — once as a {@link GifFolder} pseudo-directory (its animation
 * frames) and once as the raw {@code .gif} file itself — allowing pickers to select
 * either the whole animation or a single frame. Sub-directories are re-wrapped as
 * {@link ImageFolder} so the double-listing applies recursively.
 */
public class ImageFolder extends File
{
    private static final long serialVersionUID = 2087807134801481836L;

    public ImageFolder(String pathname)
    {
        super(pathname);
    }

    public ImageFolder(File parent, String child)
    {
        super(parent, child);
    }

    @Override
    public File[] listFiles()
    {
        return this.processFiles(super.listFiles());
    }

    private File[] processFiles(File[] files)
    {
        List<File> list = new ArrayList<File>();

        /* Total-reader guard: super.listFiles() returns null for a non-directory
         * or on an IO error. Legacy would NPE here; the workspace crash rule
         * ("unknown input -> placeholder, never a crash") wins over parity. */
        if (files == null)
        {
            return new File[0];
        }

        for (File file : files)
        {
            if (file.isFile())
            {
                if (file.getName().toLowerCase().endsWith(".gif"))
                {
                    File gif = new GifFolder(file.getPath());

                    if (gif.exists())
                    {
                        list.add(gif);
                    }
                }

                list.add(file);
            }
            else if (file.isDirectory())
            {
                list.add(new ImageFolder(file.getPath()));
            }
        }

        return list.toArray(new File[0]);
    }
}
