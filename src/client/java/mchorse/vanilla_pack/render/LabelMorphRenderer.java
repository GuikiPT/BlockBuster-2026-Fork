package mchorse.vanilla_pack.render;

import java.util.List;

import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.TextUtils;
import mchorse.metamorph.client.render.IMorphRenderer;
import mchorse.metamorph.client.render.MorphRenderContext;
import mchorse.vanilla_pack.morphs.LabelMorph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import org.joml.Matrix4f;

/**
 * Client render body for {@link LabelMorph} (roadmap P54/P53.2).
 *
 * <p>Legacy's {@code render}/{@code renderOnScreen} both funnelled into one
 * private {@code renderString}, and that sharing is what keeps a label's GUI
 * preview and its world draw laid out identically — so it is preserved here:
 * {@link #renderString} takes the matrices and vertex consumers, which the world
 * path gets from the {@link MorphRenderContext} and the GUI path from the
 * {@link DrawContext} (whose {@code getMatrices()}/{@code getVertexConsumers()}
 * are exactly that pair).</p>
 *
 * <p><b>Scale.</b> World space is {@code 1/6/8} — legacy's "approximately 16
 * letters per block" — applied as {@code (s, -s, s)} so the font's downward Y
 * grows downward in the world too. Wrapped lines advance 12 px regardless of
 * font height, which is a legacy constant, not {@code fontHeight}.</p>
 *
 * <p><b>Billboard.</b> Legacy read the GL model-view back, replaced it with
 * identity-plus-translation and transposed it — matrix surgery that cancelled
 * the camera rotation. 1.20.4 states the same intent directly as
 * {@code matrices.multiply(entityRenderDispatcher.getRotation())}, vanilla's own
 * nameplate idiom, with the sign convention that idiom implies
 * ({@code (-s, -s, s)} rather than {@code (s, -s, s)}). The two compose to the
 * same handedness, so the background quad's winding — legacy's vertex order is
 * a cyclic rotation of vanilla's nameplate quad — stays front-facing under the
 * text-background layer's culling.</p>
 *
 * <p><b>Opaque-colour quirk.</b> 1.12.2's {@code FontRenderer.renderString}
 * forced {@code alpha = 255} whenever the top six bits of the colour were zero,
 * which is why {@code LabelMorph.color}'s default {@code 0xffffff} (alpha 0) and
 * {@code shadowColor}'s default {@code 0} were visible at all. 1.20.4's
 * {@code TextRenderer} does no such thing — an alpha-0 colour is simply
 * invisible — so {@link #opaque} reproduces the legacy promotion. Without it
 * every default-coloured label morph in the port would render as nothing.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/vanilla_pack/morphs/LabelMorph.java (render/renderOnScreen/renderString/drawShadow)
 */
public class LabelMorphRenderer implements IMorphRenderer<LabelMorph>
{
    /** Legacy "approximately 16 letters per block": {@code 1 / 6 / 8}. */
    public static final float WORLD_SCALE = 1F / 6F / 8F;

    /** Legacy's fixed per-line advance in the word-wrapped branch. */
    public static final int LINE_HEIGHT = 12;

    /** Legacy's minimum wrap width floor, before the widest glyph raises it. */
    public static final int MIN_WRAP_WIDTH = 6;

    /* --------------------------------------------------------------------- */
    /* In-world                                                              */
    /* --------------------------------------------------------------------- */

    @Override
    public void render(LabelMorph morph, LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks, MorphRenderContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || context.matrices == null || context.consumers == null)
        {
            return;
        }

        MatrixStack matrices = context.matrices;
        int light = VanillaPackMorphRenderers.lightOf(morph.lighting, context.light);

        matrices.push();

        try
        {
            matrices.translate(x, y, z);

            if (morph.billboard && mc.getEntityRenderDispatcher() != null)
            {
                matrices.multiply(mc.getEntityRenderDispatcher().getRotation());
                matrices.scale(-WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
            }
            else
            {
                matrices.scale(WORLD_SCALE, -WORLD_SCALE, WORLD_SCALE);
            }

            this.renderString(morph, matrices, context.consumers, light, false);
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* On screen (GUI)                                                       */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy ignored {@code scale} and {@code alpha} entirely here — a label
     * preview is drawn at native font size, 10 units forward. Kept as-is.
     */
    @Override
    public void renderOnScreen(LabelMorph morph, PlayerEntity player, int x, int y, float scale, float alpha)
    {
        DrawContext dc = GuiDraw.getDrawContext();

        if (dc == null)
        {
            return;
        }

        MatrixStack matrices = dc.getMatrices();
        VertexConsumerProvider.Immediate consumers = dc.getVertexConsumers();

        matrices.push();

        try
        {
            matrices.translate(x, y, 10);

            this.renderString(morph, matrices, consumers, MorphRenderContext.FULL_BRIGHT, true);

            consumers.draw();
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Shared body                                                           */
    /* --------------------------------------------------------------------- */

    /**
     * Legacy private {@code renderString}, ported whole: the unwrapped branch
     * ({@code max <= 0}) centres a single line on the two anchors; the wrapped
     * branch raises {@code max} to at least the widest glyph (so a one-pixel
     * wrap width cannot loop forever), lays the lines out at {@link #LINE_HEIGHT}
     * and anchors the block by its total height. The drop shadow is a second
     * full pass at {@code (shadowX, shadowY, -0.1)}, not the font's own shadow
     * flag — legacy called the no-shadow {@code drawString} overload for both.
     */
    public void renderString(LabelMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean gui)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.textRenderer == null)
        {
            return;
        }

        TextRenderer font = mc.textRenderer;
        String text = TextUtils.processColoredText(morph.label == null ? "" : morph.label);

        if (morph.max <= 0)
        {
            int w = font.getWidth(text);
            int x = -(int) (w * morph.anchorX);
            int y = -(int) (font.fontHeight * morph.anchorY);

            this.drawShadow(morph, matrices, consumers, light, gui, x, y, w, font.fontHeight);

            if (morph.shadow)
            {
                matrices.push();
                matrices.translate(morph.shadowX, morph.shadowY, -0.1F);
                font.draw(text, x, y, opaque(morph.shadowColor), false, matrix(matrices), consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
                matrices.pop();
            }

            font.draw(text, x, y, opaque(morph.color), false, matrix(matrices), consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        else
        {
            int max = wrapWidth(font, text, morph.max);
            List<OrderedText> labels = font.wrapLines(StringVisitable.plain(text), max);
            int h = blockHeight(labels.size(), font.fontHeight);
            int y = -(int) (h * morph.anchorY);

            this.drawShadow(morph, matrices, consumers, light, gui, -(int) (max * morph.anchorX), y, max, h);

            if (morph.shadow)
            {
                matrices.push();
                matrices.translate(morph.shadowX, morph.shadowY, -0.1F);

                int shadowY = y;

                for (OrderedText label : labels)
                {
                    int w = font.getWidth(label);

                    font.draw(label, -(int) (w * morph.anchorX), shadowY, opaque(morph.shadowColor), false, matrix(matrices), consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
                    shadowY += LINE_HEIGHT;
                }

                matrices.pop();
            }

            for (OrderedText label : labels)
            {
                int w = font.getWidth(label);

                font.draw(label, -(int) (w * morph.anchorX), y, opaque(morph.color), false, matrix(matrices), consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
                y += LINE_HEIGHT;
            }
        }
    }

    /**
     * Legacy's background quad, drawn {@code 0.2} behind the text and padded by
     * {@code offset} on every side. Fully transparent backgrounds (the default)
     * skip the draw, exactly as legacy's {@code color.a <= 0} guard did.
     *
     * <p>The two paths use each dimension's own primitive rather than one shared
     * one: in the GUI, {@link DrawContext#fill} is the matrix-aware fill (mclib's
     * {@code GuiDraw.drawRect} bypasses the {@code DrawContext} matrix stack,
     * which this path translates, so it would draw in the wrong place); in the
     * world it is a {@code POSITION_COLOR_LIGHT} quad on
     * {@link RenderLayer#getTextBackground()}, vanilla's own nameplate-background
     * layer, in legacy's vertex order.</p>
     */
    protected void drawShadow(LabelMorph morph, MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean gui, int x, int y, int w, int h)
    {
        Color color = ColorUtils.COLOR.set(morph.background, true);

        if (color.a <= 0)
        {
            return;
        }

        float o = morph.offset;

        matrices.push();
        matrices.translate(0, 0, -0.2F);

        try
        {
            if (gui)
            {
                DrawContext dc = GuiDraw.getDrawContext();

                if (dc != null)
                {
                    dc.fill((int) (x - o), (int) (y - o), (int) (x + w + o), (int) (y + h + o), morph.background);
                }
            }
            else
            {
                VertexConsumer buffer = consumers.getBuffer(RenderLayer.getTextBackground());
                Matrix4f matrix = matrix(matrices);

                buffer.vertex(matrix, x + w + o, y - o, 0).color(color.r, color.g, color.b, color.a).light(light).next();
                buffer.vertex(matrix, x - o, y - o, 0).color(color.r, color.g, color.b, color.a).light(light).next();
                buffer.vertex(matrix, x - o, y + h + o, 0).color(color.r, color.g, color.b, color.a).light(light).next();
                buffer.vertex(matrix, x + w + o, y + h + o, 0).color(color.r, color.g, color.b, color.a).light(light).next();
            }
        }
        finally
        {
            matrices.pop();
        }
    }

    /* --------------------------------------------------------------------- */
    /* Pure layout helpers (headless-testable)                               */
    /* --------------------------------------------------------------------- */

    /**
     * 1.12.2 {@code FontRenderer.renderString}'s implicit alpha: a colour whose
     * top six bits are all zero is promoted to fully opaque. See the class note.
     */
    public static int opaque(int color)
    {
        return (color & 0xfc000000) == 0 ? color | 0xff000000 : color;
    }

    /**
     * Legacy's wrap-width floor: at least 6, and at least as wide as the widest
     * glyph in the text, so a narrow {@code max} cannot produce a line that
     * fits nothing.
     */
    public static int wrapWidth(TextRenderer font, String text, int max)
    {
        int min = MIN_WRAP_WIDTH;

        for (int i = 0; i < text.length(); i++)
        {
            min = Math.max(font.getWidth(String.valueOf(text.charAt(i))), min);
        }

        return MathUtils.clamp(max, min, Integer.MAX_VALUE);
    }

    /**
     * Legacy's wrapped-block height: the line advance is {@link #LINE_HEIGHT},
     * the last line contributes the font height instead, and the line count is
     * clamped at 101 lines for the purpose of the anchor.
     */
    public static int blockHeight(int lines, int fontHeight)
    {
        return MathUtils.clamp(lines - 1, 0, 100) * LINE_HEIGHT + fontHeight;
    }

    private static Matrix4f matrix(MatrixStack matrices)
    {
        return matrices.peek().getPositionMatrix();
    }
}
