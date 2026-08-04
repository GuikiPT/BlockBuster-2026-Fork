package mchorse.mclib.utils.wav;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.List;

/**
 * Waveform class — port of McLib 2.4.3's {@code Waveform} (roadmap P18).
 *
 * Data generation ({@link #populate}) is verbatim legacy math and testable
 * headlessly. Texture building moved from AWT {@code BufferedImage} +
 * {@code TextureUtil.uploadTextureImage} to {@code NativeImage} uploads; the
 * legacy {@code Graphics.drawRect(j, y, 1, h)} column bars are reproduced as
 * the equivalent two-pixel-wide columns.
 */
public class Waveform
{
    public float[] average;
    public float[] maximum;

    private List<WaveformSprite> sprites = new ArrayList<WaveformSprite>();
    private int w;
    private int h;
    private int pixelsPerSecond;

    public void generate(Wave data, int pixelsPerSecond, int height)
    {
        if (data.getBytesPerSample() != 2)
        {
            throw new IllegalStateException("Waveform generation doesn't support non 16-bit audio data!");
        }

        this.populate(data, pixelsPerSecond, height);
        this.render();
    }

    /**
     * Sprite-split widths for a waveform of pixel-width {@code w} tiled into
     * sprites no wider than {@code maxTextureSize} (legacy {@code render()} used
     * {@code GL_MAX_TEXTURE_SIZE / 2}). Extracted pure for headless testing
     * (P190): e.g. {@code (5000, 2048) -> [2048, 2048, 904]}, {@code (0, n) -> []}.
     */
    public static int[] computeSpriteWidths(int w, int maxTextureSize)
    {
        int count = (int) Math.ceil(w / (double) maxTextureSize);
        int[] widths = new int[count];
        int offset = 0;

        for (int t = 0; t < count; t++)
        {
            widths[t] = Math.min(w - offset, maxTextureSize);
            offset += maxTextureSize;
        }

        return widths;
    }

    public void render()
    {
        this.delete();

        int maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 2;
        int[] widths = computeSpriteWidths(this.w, maxTextureSize);
        int offset = 0;

        final int white = 0xFFFFFFFF;
        /* AWT Color.LIGHT_GRAY = (192, 192, 192); NativeImage is ABGR-packed */
        final int lightGray = 0xFFC0C0C0;

        for (int t = 0; t < widths.length; t++)
        {
            int texture = TextureUtil.generateTextureId();
            int width = widths[t];

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, this.h, true);

            for (int i = offset, j = 0, c = Math.min(offset + width, this.average.length); i < c; i++, j++)
            {
                float average = this.average[i];
                float maximum = this.maximum[i];

                int maxHeight = (int) (maximum * this.h);
                int avgHeight = (int) (average * (this.h - 1)) + 1;

                if (avgHeight > 0)
                {
                    this.drawColumn(image, j, this.h / 2 - maxHeight / 2, maxHeight, white);
                    this.drawColumn(image, j, this.h / 2 - avgHeight / 2, avgHeight, lightGray);
                }
            }

            TextureUtil.prepareImage(texture, width, this.h);
            image.upload(0, 0, 0, true);

            GlStateManager._bindTexture(texture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);

            this.sprites.add(new WaveformSprite(texture, width));

            offset += maxTextureSize;
        }
    }

    /**
     * Replicates legacy {@code Graphics.drawRect(x, y, 1, height)}: a
     * two-pixel-wide vertical bar spanning {@code [y, y + height]} inclusive.
     */
    private void drawColumn(NativeImage image, int x, int y, int height, int color)
    {
        for (int i = y; i <= y + height; i++)
        {
            if (i < 0 || i >= this.h)
            {
                continue;
            }

            if (x < image.getWidth())
            {
                image.setColor(x, i, color);
            }

            if (x + 1 < image.getWidth())
            {
                image.setColor(x + 1, i, color);
            }
        }
    }

    public void populate(Wave data, int pixelsPerSecond, int height)
    {
        this.pixelsPerSecond = pixelsPerSecond;
        this.w = (int) (data.getDuration() * pixelsPerSecond);
        this.h = height;
        this.average = new float[this.w];
        this.maximum = new float[this.w];

        int region = data.getScanRegion(pixelsPerSecond);

        for (int i = 0; i < this.w; i ++)
        {
            int offset = i * region;
            int count = 0;
            float average = 0;
            float maximum = 0;

            for (int j = 0; j < region; j += 2 * data.numChannels)
            {
                if (offset + j + 1 >= data.data.length)
                {
                    break;
                }

                byte a = data.data[offset + j];
                byte b = data.data[offset + j + 1];
                float sample = a + (b << 8);

                maximum = Math.max(maximum, Math.abs(sample));
                average += Math.abs(sample);
                count++;
            }

            average /= count;
            average /= 0xffff / 2;
            maximum /= 0xffff / 2;

            this.average[i] = average;
            this.maximum[i] = maximum;
        }
    }

    public void delete()
    {
        for (WaveformSprite sprite : this.sprites)
        {
            TextureUtil.releaseTextureId(sprite.texture);
        }

        this.sprites.clear();
    }

    public boolean isCreated()
    {
        return !this.sprites.isEmpty();
    }

    public int getPixelsPerSecond()
    {
        return this.pixelsPerSecond;
    }

    public int getWidth()
    {
        return this.w;
    }

    public int getHeight()
    {
        return this.h;
    }

    public List<WaveformSprite> getSprites()
    {
        return this.sprites;
    }

    public void draw(int x, int y, int u, int v, int w, int h)
    {
        draw(x, y, u, v, w, h, this.h);
    }

    /**
     * Draw the waveform out of multiple sprites of desired cropped region
     */
    public void draw(int x, int y, int u, int v, int w, int h, int height)
    {
        int offset = 0;

        for (WaveformSprite sprite : this.sprites)
        {
            int sw = sprite.width;
            offset += sw;

            if (w <= 0)
            {
                break;
            }

            if (u >= offset)
            {
                continue;
            }

            int so = offset - u;

            RenderSystem.setShaderTexture(0, sprite.texture);
            GuiDraw.drawBillboard(x, y, u, v, Math.min(w, so), h, sw, height);

            x += so;
            u += so;
            w -= so;
        }
    }

    public static class WaveformSprite
    {
        public final int texture;
        public final int width;

        public WaveformSprite(int texture, int width)
        {
            this.texture = texture;
            this.width = width;
        }
    }
}
