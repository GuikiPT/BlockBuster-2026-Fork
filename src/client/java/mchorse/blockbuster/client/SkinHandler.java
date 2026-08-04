package mchorse.blockbuster.client;

import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FilenameUtils;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.utils.BlockbusterPaths;
import net.minecraft.client.MinecraftClient;

/**
 * General skins-folder auto-sorter (roadmap P88, direct port of 1.12.2's
 * {@code mchorse.blockbuster.client.SkinHandler}).
 *
 * <p>Every 30 client ticks (wired from {@code BlockbusterClient}) this scans the
 * top level of {@code config/blockbuster/skins} and moves each image into the
 * appropriate model skins folder based on its dimensions:</p>
 * <ul>
 * <li>square, 64-multiple → {@code models/fred/skins}</li>
 * <li>2:1 aspect → {@code models/steve/skins}</li>
 * </ul>
 *
 * <p>Load-bearing legacy quirks preserved verbatim:</p>
 * <ul>
 * <li>top level only — {@code file.isFile()} gate, subdirectories are never
 * recursed and stay in place;</li>
 * <li>dimensions probed <b>header-only</b> via {@link ImageIO} readers (no full
 * decode); files with no matching {@link ImageReader} (non-images) are silently
 * skipped and left in place;</li>
 * <li>the steve/fred mapping is inverted from intuition (square → {@code fred},
 * 2:1 → {@code steve}) and the dimension gate uses {@code ||} (either multiple
 * suffices);</li>
 * <li>collision naming appends {@code _1}, {@code _2}, … up to {@code _999};
 * if all are taken the move silently does not happen (retried next rescan);</li>
 * <li>a probe exception is logged and the file stays (retried next rescan).</li>
 * </ul>
 */
public class SkinHandler
{
    /**
     * Scan the general skins folder ({@code config/blockbuster/skins}) and move
     * eligible images into {@code config/blockbuster/models/<fred|steve>/skins}.
     */
    public static void checkSkinsFolder()
    {
        checkSkinsFolder(BlockbusterPaths.skins().toFile(), BlockbusterPaths.models().toFile());
    }

    /**
     * Test/seam-visible overload: scan {@code skinsFolder}, moving into
     * {@code modelsRoot/<fred|steve>/skins}. Mirrors the legacy static behavior
     * with the two folder roots injected instead of read from
     * {@code ClientProxy.skinsFolder} / {@code CommonProxy.configFile}.
     */
    public static void checkSkinsFolder(File skinsFolder, File modelsRoot)
    {
        if (skinsFolder == null || !skinsFolder.exists())
        {
            return;
        }

        File[] files = skinsFolder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isFile())
            {
                tryReadingMovingSkinFile(file, modelsRoot);
            }
        }
    }

    private static void tryReadingMovingSkinFile(File file, File modelsRoot)
    {
        try
        {
            FileInputStream stream = new FileInputStream(file);

            try (ImageInputStream in = ImageIO.createImageInputStream(stream))
            {
                final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);

                if (readers.hasNext())
                {
                    ImageReader reader = readers.next();

                    try
                    {
                        reader.setInput(in);
                        int w = reader.getWidth(0);
                        int h = reader.getHeight(0);
                        reader.dispose();
                        stream.close();

                        reader = null;
                        tryMovingSkin(file, w, h, modelsRoot);
                    }
                    finally
                    {
                        if (reader != null)
                        {
                            reader.dispose();
                            stream.close();
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static void tryMovingSkin(File file, int w, int h, File modelsRoot)
    {
        float aspect = w / (float) h;

        /* Must be 1 to 1 or 2 to 1 aspect ratio */
        if (!(aspect == 2F || aspect == 1F))
        {
            return;
        }

        int hf = aspect == 1F ? 64 : 32;

        if (!(w % 64 == 0 || h % hf == 0))
        {
            return;
        }

        moveToDestination(hf == 64 ? "fred" : "steve", file, modelsRoot);
    }

    /**
     * Move {@code input} into {@code modelsRoot/<folder>/skins}, appending
     * {@code _1}…{@code _999} on name collision. Fires the
     * {@code model.skin_moved} success message on a successful rename (guarded
     * so it is a no-op when no client player is present, e.g. headless tests).
     * Package-visible for tests, which assert the on-disk destination.
     */
    static File moveToDestination(String folder, File input, File modelsRoot)
    {
        String name = input.getName();
        File file = new File(modelsRoot, folder + "/skins/" + name);

        for (int i = 1; file.exists() && i < 1000; i++)
        {
            file = new File(modelsRoot, folder + "/skins/" + FilenameUtils.getBaseName(name) + "_" + i + "." + FilenameUtils.getExtension(name));
        }

        if (!file.exists())
        {
            boolean moved = input.renameTo(file);

            if (moved)
            {
                sendMovedMessage(name, folder, file.getName());

                return file;
            }
        }

        return null;
    }

    private static void sendMovedMessage(String name, String folder, String filename)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.player != null)
        {
            Blockbuster.l10n.success(mc.player, "model.skin_moved", name, "blockbuster." + folder, filename);
        }
    }
}
