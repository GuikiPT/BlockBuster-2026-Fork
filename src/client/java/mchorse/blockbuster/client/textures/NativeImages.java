package mchorse.blockbuster.client.textures;

import java.awt.image.BufferedImage;

import net.minecraft.client.texture.NativeImage;

/**
 * {@link NativeImage} conversion helpers.
 *
 * <p>The legacy Blockbuster texture pipeline (GIF frames P89, URL skins P90,
 * multiskins P91) decoded pixels into an AWT {@link BufferedImage} and uploaded
 * them with {@code TextureUtil.uploadTextureImage(glId, image)}. On the modern
 * stack the GL-backed texture object is a {@link NativeImage}, so this shared
 * helper converts an AWT {@code BufferedImage} into a {@code NativeImage}.</p>
 *
 * <p><b>Pixel order:</b> {@link BufferedImage#getRGB} returns ARGB packed as
 * {@code 0xAARRGGBB}, whereas {@link NativeImage} (format {@code RGBA}) stores
 * and expects little-endian {@code 0xAABBGGRR} (ABGR). This helper swaps the red
 * and blue channels per pixel so colours survive the round-trip.</p>
 */
public final class NativeImages
{
    private NativeImages()
    {}

    /**
     * Convert an AWT {@link BufferedImage} into a fresh {@link NativeImage}
     * (RGBA). The caller owns the returned image and must {@code close()} it
     * (or hand it to a {@code NativeImageBackedTexture}, which closes it).
     */
    public static NativeImage fromBufferedImage(BufferedImage image)
    {
        int width = image.getWidth();
        int height = image.getHeight();

        NativeImage native_ = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                native_.setColor(x, y, argbToAbgr(image.getRGB(x, y)));
            }
        }

        return native_;
    }

    /**
     * Convert a raw ARGB pixel array into a fresh {@link NativeImage} (RGBA).
     * The 1.12.2 dynamic textures this replaces ({@code DynamicTexture} for VOX
     * palettes, the generated OBJ solid-colour strip) both held plain
     * {@code 0xAARRGGBB} ints, so the same channel swap applies.
     *
     * <p>Pixels are read row-major; a short array is padded with transparent
     * black rather than throwing (totality — a malformed palette must not crash
     * a model render).</p>
     */
    public static NativeImage fromArgb(int[] pixels, int width, int height)
    {
        NativeImage native_ = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int index = y * width + x;
                int argb = pixels != null && index < pixels.length ? pixels[index] : 0;

                native_.setColor(x, y, argbToAbgr(argb));
            }
        }

        return native_;
    }

    /**
     * Swap the red and blue channels: {@code 0xAARRGGBB} (AWT ARGB) to
     * {@code 0xAABBGGRR} (NativeImage little-endian ABGR).
     */
    public static int argbToAbgr(int argb)
    {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
