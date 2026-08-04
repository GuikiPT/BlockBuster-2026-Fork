package mchorse.blockbuster.utils;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Texture disk/byte helpers (roadmap P87/P91).
 *
 * <p>The GL-touching pieces of the legacy texture pipeline live in the client
 * source set; the pure, headless-testable bits (the export file-naming contract
 * and the ARGB&harr;RGBA byte reorders shared by {@code MipmapTexture} and
 * {@code MultiskinThread}) are gathered here so the golden tests can exercise
 * them without a GL context.</p>
 */
public class TextureUtils
{
    /**
     * Legacy naming contract for exported textures / screenshots: try
     * {@code name.png}, then {@code name1.png}, {@code name2.png}, &hellip;
     * (no separator between {@code name} and the index, index starting at 1),
     * returning the first path that does not yet exist.
     *
     * <p>Load-bearing: SkinHandler (P88) uses a <em>different</em> pattern
     * ({@code name_1.png}); do not unify them.</p>
     */
    public static File getFirstAvailableFile(File folder, String name)
    {
        File file = new File(folder, name + ".png");
        int index = 0;

        while (file.exists())
        {
            index += 1;
            file = new File(folder, name + index + ".png");
        }

        return file;
    }

    /**
     * Reproduce the legacy {@code MipmapTexture.bytesFromBuffer} /
     * {@code MultiskinThread.bytesFromBuffer} reorder: an ARGB
     * {@link BufferedImage} is read out into a flat {@code R,G,B,A} byte array
     * (row-major, top-to-bottom) suitable for a {@code GL_RGBA} upload.
     *
     * <p>Both legacy call sites had byte-identical code; the port keeps a
     * single pure implementation and wraps it in a direct
     * {@code ByteBuffer} at the GL boundary.</p>
     */
    public static byte[] bytesFromBuffer(BufferedImage image)
    {
        int w = image.getWidth();
        int h = image.getHeight();

        byte[] buffer = new byte[w * h * 4];
        int[] pixels = new int[w * h];

        image.getRGB(0, 0, w, h, pixels, 0, w);

        int i = 0;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int pixel = pixels[y * w + x];

                buffer[i++] = (byte) ((pixel >> 16) & 0xFF);
                buffer[i++] = (byte) ((pixel >> 8) & 0xFF);
                buffer[i++] = (byte) (pixel & 0xFF);
                buffer[i++] = (byte) ((pixel >> 24) & 0xFF);
            }
        }

        return buffer;
    }

    /**
     * Inverse of {@link #bytesFromBuffer} for the export path: a flat
     * {@code R,G,B,A} byte buffer read back from GL
     * ({@code glGetTexImage(GL_RGBA, GL_UNSIGNED_BYTE)}) is repacked into a
     * row-major ARGB {@code int[]} — the exact repack legacy
     * {@code GuiTextureManagerPanel.export()} performed before writing the PNG.
     */
    public static int[] argbFromRgba(byte[] rgba, int width, int height)
    {
        int[] pixels = new int[width * height];

        for (int i = 0; i < pixels.length; i++)
        {
            int r = rgba[i * 4] & 0xFF;
            int g = rgba[i * 4 + 1] & 0xFF;
            int b = rgba[i * 4 + 2] & 0xFF;
            int a = rgba[i * 4 + 3] & 0xFF;

            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        return pixels;
    }

    /**
     * The pure tail of legacy {@code GuiTextureManagerPanel.export()} (P140): a
     * flat {@code R,G,B,A} buffer read back from GL is repacked
     * ({@link #argbFromRgba}) into a row-major ARGB {@link BufferedImage} of
     * {@code TYPE_INT_ARGB} — the exact image the panel hands to
     * {@code ImageIO.write(image, "png", file)}. Factored out so the pixel path
     * is testable without a GL context.
     */
    public static BufferedImage imageFromRgba(byte[] rgba, int width, int height)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        image.setRGB(0, 0, width, height, argbFromRgba(rgba, width, height), 0, width);

        return image;
    }
}
