package mchorse.mclib.client.gui.utils;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.tooltips.styles.TooltipStyle;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.IInterpolation;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Port of McLib 2.4.3's {@code InterpolationRenderer} (roadmap P38) — the
 * 140×130 interpolation-preview panel: A→B curve over a faint 40-segment
 * grid, name, wrapped tooltip text, and an animated dot cycling over
 * {@code duration + 20} ticks.
 *
 * GL boundary: legacy {@code GL_LINES} immediate mode →
 * {@code VertexFormat.DrawMode.DEBUG_LINES} + POSITION_COLOR with the
 * position-color shader; {@code glLineWidth} → {@code RenderSystem.lineWidth}
 * (DEBUG_LINES honors it on most drivers; the 2px/3px distinction is
 * best-effort on core profile). No-op headless.
 */
public class InterpolationRenderer
{
    public static void drawInterpolationPreview(IInterpolation interp, GuiContext context, int x, int y, float anchorX, float anchorY, int duration)
    {
        if (interp == null)
        {
            return;
        }

        final float iterations = 40;
        final float padding = 50;

        int w = 140;
        int h = 130;

        TooltipStyle style = TooltipStyle.get();
        String tooltip = interp.getTooltip();
        List<String> lines = tooltip.isEmpty() ? ImmutableList.of() : GuiDraw.listFormattedStringToWidth(tooltip, w - 20, (s) -> GuiDraw.textWidth(context.font, s));
        int ah = lines.isEmpty() ? 0 : lines.size() * (GuiDraw.fontHeight(context.font) + 4);

        y = MathUtils.clamp(y, 0, context.screen.height - h - ah);

        x -= (int) (w * anchorX);
        y -= (int) (h * anchorY);

        Area.SHARED.set(x, y, w, h + ah);
        style.drawBackground(Area.SHARED);

        Color fg = ColorUtils.COLOR.set(style.getForegroundColor(), false);
        int font = style.getTextColor();

        GuiDraw.drawString(context.font, interp.getName(), x + 10, y + 10, font);

        for (int i = 0; i < lines.size(); i++)
        {
            GuiDraw.drawString(context.font, lines.get(i), x + 10, y + h - 5 + i * (GuiDraw.fontHeight(context.font) + 4), font);
        }

        DrawContext drawContext = GuiDraw.getDrawContext();

        if (drawContext == null)
        {
            return;
        }

        Matrix4f m = drawContext.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2F);

        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        builder.vertex(m, x + 10, y + 20, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + 10, y + h - 10, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w / 2, y + 20, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w / 2, y + h - 10, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w - 10, y + 20, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w - 10, y + h - 10, 0).color(fg.r, fg.g, fg.b, 0.2F).next();

        builder.vertex(m, x + 10, y + 20, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w - 10, y + 20, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + 10, y + 20 + (h - 30) / 2, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w - 10, y + 20 + (h - 30) / 2, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + 10, y + h - 10, 0).color(fg.r, fg.g, fg.b, 0.2F).next();
        builder.vertex(m, x + w - 10, y + h - 10, 0).color(fg.r, fg.g, fg.b, 0.2F).next();

        builder.vertex(m, x + 10, y + h - 10 - padding / 2, 0).color(fg.r, fg.g, fg.b, 0.11F).next();
        builder.vertex(m, x + w - 10, y + h - 10 - padding / 2, 0).color(fg.r, fg.g, fg.b, 0.11F).next();
        builder.vertex(m, x + 10, y + 20 + padding / 2, 0).color(fg.r, fg.g, fg.b, 0.11F).next();
        builder.vertex(m, x + w - 10, y + 20 + padding / 2, 0).color(fg.r, fg.g, fg.b, 0.11F).next();

        Tessellator.getInstance().draw();

        RenderSystem.lineWidth(3F);
        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 1; i <= iterations; i++)
        {
            float factor0 = (i - 1) / iterations;
            float value0 = 1 - interp.interpolate(0, 1, factor0);
            float factor1 = i / iterations;
            float value1 = 1 - interp.interpolate(0, 1, factor1);

            float x1 = x + 10 + factor1 * (w - 20);
            float x2 = x + 10 + factor0 * (w - 20);
            float y1 = y + 20 + padding / 2 + value1 * (h - 30 - padding);
            float y2 = y + 20 + padding / 2 + value0 * (h - 30 - padding);

            builder.vertex(m, x1, y1, 0).color(fg.r, fg.g, fg.b, 1F).next();
            builder.vertex(m, x2, y2, 0).color(fg.r, fg.g, fg.b, 1F).next();
        }

        Tessellator.getInstance().draw();

        RenderSystem.lineWidth(1F);
        RenderSystem.disableBlend();

        GuiDraw.drawString(context.font, "A", x + 14, (int) (y + h - 10 - padding / 2) + 4, font);
        GuiDraw.drawString(context.font, "B", x + w - 19, (int) (y + 20 + padding / 2) - GuiDraw.fontHeight(context.font) - 4, font);

        float tick = ((context.tick + context.partialTicks) % (duration + 20)) / (float) duration;
        float factor = MathUtils.clamp(tick, 0, 1);
        int px = x + w - 5;
        int py = y + 20 + (int) (padding / 2) + (int) ((1 - interp.interpolate(0, 1, factor)) * (h - 30 - padding));

        GuiDraw.drawRect(px - 2, py - 2, px + 2, py + 2, 0xff000000 + fg.getRGBColor());
    }
}
