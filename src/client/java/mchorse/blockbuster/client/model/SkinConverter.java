package mchorse.blockbuster.client.model;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

/**
 * Skin converter core (P74).
 *
 * <p>Pure {@code java.awt} port of the conversion logic from 1.12.2's
 * {@code mchorse.blockbuster.commands.model.SubCommandModelConvertSkin} — the
 * {@code /model convert <steve|fred> <skin>} command. Extracted into a testable
 * core because the command wrapper (resource resolution via the {@code b.a} skin
 * domain, writing into {@code config/blockbuster/models/<oppositeModel>/skins/})
 * belongs to the S5-P73 Brigadier {@code /model} tree and the S7 skin system;
 * this class holds only the aspect validation and the pixel-copy conversion,
 * which are fully headless and unit-testable.</p>
 *
 * <ul>
 *   <li><b>steve</b> (64×32 → 64×64): copies the single-armed legacy skin onto a
 *       doubled-height canvas and mirrors the 12 limb rectangles (coordinates
 *       copied verbatim from vanilla {@code ImageBufferDownload}), scaling by
 *       {@code s = w / 64} for HD skins.</li>
 *   <li><b>fred</b> (64×64 → 64×32): crops to the top half.</li>
 * </ul>
 *
 * <p>Both branches allocate {@code BufferedImage.TYPE_INT_ARGB} (the legacy
 * {@code new BufferedImage(w, h, 2)} magic constant) so alpha survives.</p>
 */
public class SkinConverter
{
    /**
     * Validate the source skin aspect ratio, exactly as 1.12.2 did.
     *
     * <p>Accepts square skins whose side is a multiple of 64 (64×64, 128×128, …)
     * and 2:1 skins whose width is a multiple of 64 with half-height a multiple
     * of 32 (64×32, 128×64, …).</p>
     */
    public static boolean isValidAspect(int w, int h)
    {
        boolean one = w == h;

        return w % 64 == 0 && h % (one ? 64 : 32) == 0 && (one || w == h * 2);
    }

    /**
     * Convert a skin image.
     *
     * @param image the source skin
     * @param steve {@code true} to convert a 64×32-style skin up to 64×64 with
     *              mirrored limbs; {@code false} to crop a 64×64 skin down to its
     *              64×32 top half.
     * @return a freshly-allocated converted image (the caller owns it)
     */
    public static BufferedImage convert(BufferedImage image, boolean steve)
    {
        int w = image.getWidth();
        int h = image.getHeight();

        BufferedImage target;

        if (steve)
        {
            /* Convert to 64x64 */
            target = new BufferedImage(w, h * 2, BufferedImage.TYPE_INT_ARGB);

            Graphics graphics = target.getGraphics();
            float s = w / 64F;

            /* These coordinates were copied from ImageBufferDownload class */
            graphics.drawImage(image, 0, 0, null);
            graphics.setColor(new Color(0, 0, 0, 0));
            /* Legacy quirk (do not "fix"): filling with a fully transparent
             * color under the default SrcOver composite blends as dst*(1-0) =
             * dst, so this rectangle does NOT clear opaque pixels. Reproduced
             * verbatim for byte parity with Blockbuster 2.7.2. */
            graphics.fillRect(0, h / 2, w, h / 2);
            drawImage(graphics, target, 24, 48, 20, 52, 4, 16, 8, 20, s);
            drawImage(graphics, target, 28, 48, 24, 52, 8, 16, 12, 20, s);
            drawImage(graphics, target, 20, 52, 16, 64, 8, 20, 12, 32, s);
            drawImage(graphics, target, 24, 52, 20, 64, 4, 20, 8, 32, s);
            drawImage(graphics, target, 28, 52, 24, 64, 0, 20, 4, 32, s);
            drawImage(graphics, target, 32, 52, 28, 64, 12, 20, 16, 32, s);
            drawImage(graphics, target, 40, 48, 36, 52, 44, 16, 48, 20, s);
            drawImage(graphics, target, 44, 48, 40, 52, 48, 16, 52, 20, s);
            drawImage(graphics, target, 36, 52, 32, 64, 48, 20, 52, 32, s);
            drawImage(graphics, target, 40, 52, 36, 64, 44, 20, 48, 32, s);
            drawImage(graphics, target, 44, 52, 40, 64, 40, 20, 44, 32, s);
            drawImage(graphics, target, 48, 52, 44, 64, 52, 20, 56, 32, s);
        }
        else
        {
            /* Else, convert from 64x64 to 64x32 */
            target = new BufferedImage(w, h / 2, BufferedImage.TYPE_INT_ARGB);

            Graphics graphics = target.getGraphics();
            graphics.drawImage(image, 0, 0, (ImageObserver) null);
        }

        return target;
    }

    /**
     * Draw parts of the image to another image using graphics and with
     * a custom scale so it could support high resolution skins
     */
    private static void drawImage(Graphics graphics, BufferedImage image, float a1, float a2, float b1, float b2, float c1, float c2, float d1, float d2, float s)
    {
        graphics.drawImage(image, (int) (a1 * s), (int) (a2 * s), (int) (b1 * s), (int) (b2 * s), (int) (c1 * s), (int) (c2 * s), (int) (d1 * s), (int) (d2 * s), null);
    }
}
