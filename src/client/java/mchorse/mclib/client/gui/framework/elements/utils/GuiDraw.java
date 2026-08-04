package mchorse.mclib.client.gui.framework.elements.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Icon;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.ToIntFunction;

/**
 * Port of McLib 2.4.3's {@code GuiDraw} (roadmap P31) — every public legacy
 * signature is preserved; only the GL guts changed. GL1→1.20.4 mapping
 * decisions:
 *
 * - Colored quads/gradients: {@code Tessellator} +
 *   {@code VertexFormats.POSITION_COLOR} with the vanilla position-color
 *   shader ({@code GameRenderer::getPositionColorProgram}); smooth shading is
 *   the core-profile default, so legacy {@code shadeModel(GL_SMOOTH)} pairs
 *   disappear.
 * - Textured quads: {@code POSITION_TEXTURE} + position-tex shader; the
 *   caller binds the texture via {@code RenderSystem.setShaderTexture} (the
 *   legacy {@code renderEngine.bindTexture} position).
 * - {@code Gui.drawRect} (inherited from 1.12's {@code Gui}) becomes
 *   {@link #drawRect} here.
 * - Scissors keep our own stack with the legacy clamping semantics + 1px
 *   floor and call {@code RenderSystem.enableScissor} with
 *   framebuffer-pixel rects — NOT {@code DrawContext.enableScissor}, whose
 *   stack cannot restore the previous scissor on {@code unscissor}.
 * - Text goes through the current frame's {@link DrawContext} (bound by
 *   {@code GuiBase.render} via {@link #bindDrawContext}).
 * - {@code GL_TRIANGLE_FAN} still exists as a vanilla
 *   {@code VertexFormat.DrawMode}, so the circle shadows keep their fan
 *   layout.
 * - Vertices are transformed by the bound {@code DrawContext}'s matrix stack
 *   when present (legacy relied on {@code GlStateManager.translate} state).
 */
public class GuiDraw
{
    private final static Stack<Area> scissors = new Stack<Area>();

    /**
     * The current frame's vanilla draw context — bound by
     * {@code GuiBase.render} each frame (1.20.4 bridge, additive).
     */
    private static DrawContext drawContext;

    public static void bindDrawContext(DrawContext context)
    {
        drawContext = context;
    }

    public static DrawContext getDrawContext()
    {
        return drawContext;
    }

    /**
     * Flush everything the bound {@link DrawContext} has buffered (1.20.4
     * bridge, additive — no legacy counterpart).
     *
     * <p><b>Why the framework needs this.</b> 1.12.2's font renderer drew
     * immediately, so the GUI framework's draw order <i>was</i> the paint order:
     * a popup drawn after a panel covered that panel's labels, and a label
     * outside a scissor rect was clipped. On 1.20.4 {@code DrawContext.drawText}
     * only <i>buffers</i> into the frame's {@code VertexConsumerProvider
     * .Immediate}, which vanilla flushes once, after the whole screen (and after
     * the HUD, which shares the same context). Every quad this class draws goes
     * out immediately through the tessellator, so without an explicit flush
     * <b>all</b> text lands on top of <b>all</b> geometry, whatever the order —
     * that is the overlay panels showing the labels of the panel underneath, and
     * the F3 overlay showing through the dashboard — and text is clipped by
     * whatever scissor happens to be set at the end of the frame rather than the
     * one that was active when it was submitted.</p>
     *
     * <p>So {@link #drawString} flushes right after it submits: the buffer never
     * outlives the draw call that filled it, and both painter's order and
     * scissoring behave exactly as they did on 1.12.2. Cheap when there is
     * nothing buffered ({@code Immediate.draw()} skips empty layers), and GUI
     * text volumes are small enough that the extra draw calls do not register.</p>
     *
     * <p><b>This is only half of the text-ordering contract.</b> A flush fixes
     * <i>when</i> the glyphs are painted; it does nothing about the depth values
     * they leave in the buffer, which outlive every flush and reject later quads
     * on their own. {@link #flattenDepth} is the other half — see its note.</p>
     */
    public static void flush()
    {
        if (drawContext != null)
        {
            drawContext.draw();
        }
    }

    /**
     * Collapse a GUI matrix' <b>z column</b> so every vertex transformed
     * through it lands on one depth plane, whatever local z it carries
     * (1.20.4 bridge, additive — no legacy counterpart). Returns the same
     * instance, mutated.
     *
     * <p><b>Why the framework needs this.</b> 1.20.4's {@code TextRenderer}
     * draws <i>shadowed</i> text as two layers and pushes the glyph layer
     * {@code FORWARD_SHIFT = (0, 0, 0.03)} <b>in front</b> of the shadow, so
     * the two never z-fight. The {@code "text"} render layer keeps the
     * {@code MultiPhaseParameters} defaults — {@code LEQUAL_DEPTH_TEST} and
     * {@code ALL_MASK} — so those glyph quads <b>write depth</b>, and they
     * write it <i>nearer</i> than the GUI plane the rest of the framework
     * draws on ({@code z = 0}). Every quad drawn afterwards at {@code z = 0}
     * then fails {@code GL_LEQUAL} on exactly the glyph's pixels and is
     * discarded: a popup drawn over a panel covers the panel's backgrounds,
     * its buttons and its text <i>shadows</i> — everything at {@code z = 0} —
     * but the glyphs themselves survive at full brightness, punched through
     * the popup. That is the reported "the text under it doesn't fade behind
     * the menu, the part of the menu does cover correctly".</p>
     *
     * <p>1.12.2 had no such shift: {@code FontRenderer.drawString} rendered the
     * shadow and the glyph at the same {@code zLevel}, so {@code GL_LEQUAL}
     * always compared equal and the GUI was a pure painter's algorithm. Zeroing
     * the z column restores exactly that — {@code Matrix4f.translate(0, 0, dz)}
     * contributes {@code m20 * dz} to the translation, which is {@code 0} once
     * the column is gone, so <i>both</i> text layers land on the GUI plane.
     * Nothing else changes: the shadow keeps its {@code Glyph.getShadowOffset()}
     * x/y offset and its 0.25 brightness, so the text looks identical.</p>
     *
     * <p>Note that flushing ({@link #flush()}) cannot fix this on its own — the
     * flush controls when the glyphs are <i>painted</i>, not the depth values
     * they leave behind, and the depth footprint outlives any number of
     * flushes. Both are needed, which is why {@link #drawString} does both.</p>
     */
    public static Matrix4f flattenDepth(Matrix4f matrix)
    {
        return matrix.m20(0F).m21(0F).m22(0F).m23(0F);
    }

    /**
     * Drop the depth footprint the frame has accumulated so far (1.20.4 bridge,
     * additive — no legacy counterpart).
     *
     * <p>{@link #flattenDepth} keeps <i>our own</i> text on the GUI plane, but
     * the screen does not own the whole frame: {@code GameRenderer.render}
     * clears depth <b>once</b>, then draws {@code InGameHud} and only then the
     * screen, through the same {@code DrawContext} and the same depth buffer. So
     * the hotbar counts, the chat, the action bar and the F3 overlay all write
     * their glyph layers 0.03 in front of the GUI plane <i>before</i> a
     * dashboard exists, and every panel background drawn afterwards is rejected
     * on those pixels — vanilla HUD text bleeding through an opaque panel.</p>
     *
     * <p>{@code GuiBase.render} therefore starts each frame by clearing depth,
     * right after it pushes out the HUD's buffered geometry. The framework is a
     * painter's algorithm and inherits nothing from the HUD's depth, so there is
     * nothing to preserve; {@code GuiModelRenderer} already does the same clear
     * for its 3D preview. One clear per frame.</p>
     */
    public static void clearDepth()
    {
        if (drawContext != null)
        {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    /* 1.20.4 helpers (boundary-only additions) */

    private static Matrix4f matrix()
    {
        return drawContext == null ? null : drawContext.getMatrices().peek().getPositionMatrix();
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, float r, float g, float b, float a)
    {
        Matrix4f m = matrix();

        if (m == null)
        {
            buffer.vertex(x, y, z).color(r, g, b, a).next();
        }
        else
        {
            buffer.vertex(m, x, y, z).color(r, g, b, a).next();
        }
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v)
    {
        Matrix4f m = matrix();

        if (m == null)
        {
            buffer.vertex(x, y, z).texture(u, v).next();
        }
        else
        {
            buffer.vertex(m, x, y, z).texture(u, v).next();
        }
    }

    /**
     * Replacement of legacy {@code ColorUtils.bindColor(int)}
     * ({@code GlStateManager.color}) — tints the shader color from an ARGB
     * int. Callers must reset with {@code resetColor()} (modern shader color
     * is global state).
     */
    public static void bindColor(int color)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        float a = (color >> 24 & 255) / 255F;
        float r = (color >> 16 & 255) / 255F;
        float g = (color >> 8 & 255) / 255F;
        float b = (color & 255) / 255F;

        RenderSystem.setShaderColor(r, g, b, a);
    }

    public static void resetColor()
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /**
     * 1.12.2 {@code Gui.drawRect} equivalent (the framework inherited it from
     * {@code Gui}; here it's a {@code GuiDraw} static). Coordinates are
     * swapped when inverted, exactly like legacy.
     */
    public static void drawRect(int left, int top, int right, int bottom, int color)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        if (left < right)
        {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom)
        {
            int j = top;
            top = bottom;
            bottom = j;
        }

        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertex(buffer, left, bottom, 0, r, g, b, a);
        vertex(buffer, right, bottom, 0, r, g, b, a);
        vertex(buffer, right, top, 0, r, g, b, a);
        vertex(buffer, left, top, 0, r, g, b, a);
        tessellator.draw();

        RenderSystem.disableBlend();
    }

    /* Scissoring */

    public static void scissor(int x, int y, int w, int h, GuiContext context)
    {
        scissor(context.globalX(x), context.globalY(y), w, h, context.screen.width, context.screen.height);
    }

    /**
     * Scissor (clip) the screen
     */
    public static void scissor(int x, int y, int w, int h, int sw, int sh)
    {
        Area scissor = scissors.isEmpty() ? null : scissors.peek();

        /* If it was scissored before, then clamp to the bounds of the last one */
        if (scissor != null)
        {
            Area clamped = clampScissor(scissor, x, y, w, h);

            x = clamped.x;
            y = clamped.y;
            w = clamped.w;
            h = clamped.h;
        }

        scissor = new Area(x, y, w, h);
        scissorArea(x, y, w, h, sw, sh);
        scissors.add(scissor);
    }

    /**
     * Legacy nested-scissor clamping math, extracted pure for unit testing.
     * Note the width/height are first shrunk by any negative overhang before
     * the position clamp — kept verbatim.
     */
    public static Area clampScissor(Area scissor, int x, int y, int w, int h)
    {
        w += Math.min(x - scissor.x, 0);
        h += Math.min(y - scissor.y, 0);
        x = MathUtils.clamp(x, scissor.x, scissor.ex());
        y = MathUtils.clamp(y, scissor.y, scissor.ey());
        w = MathUtils.clamp(w, 0, scissor.ex() - x);
        h = MathUtils.clamp(h, 0, scissor.ey() - y);

        return new Area(x, y, w, h);
    }

    private static void scissorArea(int x, int y, int w, int h, int sw, int sh)
    {
        /* Clipping area around scroll area */
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.getWindow() == null)
        {
            /* Headless — geometry-only mode */
            return;
        }

        int[] rect = computeScissorArea(x, y, w, h, sw, sh, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());

        RenderSystem.enableScissor(rect[0], rect[1], rect[2], rect[3]);
    }

    /**
     * GUI-coordinate → framebuffer-pixel scissor rect, extracted pure for
     * unit testing. Reproduces the legacy display/GUI ceil-ratio math and the
     * 0-size → 1px floor ({@code glScissor(0, 0, 1, 1)}) that avoided GL
     * errors (some panels rely on the 1px sliver).
     *
     * @return {@code {x, y, w, h}} in GL scissor coordinates (origin
     *         bottom-left)
     */
    public static int[] computeScissorArea(int x, int y, int w, int h, int sw, int sh, int displayW, int displayH)
    {
        float rx = (float) Math.ceil(displayW / (double) sw);
        float ry = (float) Math.ceil(displayH / (double) sh);

        int xx = (int) (x * rx);
        int yy = (int) (displayH - (y + h) * ry);
        int ww = (int) (w * rx);
        int hh = (int) (h * ry);

        if (ww == 0 || hh == 0)
        {
            return new int[] {0, 0, 1, 1};
        }

        return new int[] {xx, yy, ww, hh};
    }

    public static void unscissor(GuiContext context)
    {
        unscissor(context.screen.width, context.screen.height);
    }

    public static void unscissor(int sw, int sh)
    {
        scissors.pop();

        if (scissors.isEmpty())
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.getWindow() != null)
            {
                RenderSystem.disableScissor();
            }
        }
        else
        {
            Area area = scissors.peek();

            scissorArea(area.x, area.y, area.w, area.h, sw, sh);
        }
    }

    /* Gradients */

    public static void drawHorizontalGradientRect(int left, int top, int right, int bottom, int startColor, int endColor)
    {
        drawHorizontalGradientRect(left, top, right, bottom, startColor, endColor, 0);
    }

    /**
     * Draws a rectangle with a horizontal gradient between with specified
     * colors, the code is borrowed form drawGradient()
     */
    public static void drawHorizontalGradientRect(int left, int top, int right, int bottom, int startColor, int endColor, float zLevel)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        float a1 = (startColor >> 24 & 255) / 255.0F;
        float r1 = (startColor >> 16 & 255) / 255.0F;
        float g1 = (startColor >> 8 & 255) / 255.0F;
        float b1 = (startColor & 255) / 255.0F;
        float a2 = (endColor >> 24 & 255) / 255.0F;
        float r2 = (endColor >> 16 & 255) / 255.0F;
        float g2 = (endColor >> 8 & 255) / 255.0F;
        float b2 = (endColor & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertex(buffer, right, top, zLevel, r2, g2, b2, a2);
        vertex(buffer, left, top, zLevel, r1, g1, b1, a1);
        vertex(buffer, left, bottom, zLevel, r1, g1, b1, a1);
        vertex(buffer, right, bottom, zLevel, r2, g2, b2, a2);
        tessellator.draw();

        RenderSystem.disableBlend();
    }

    public static void drawVerticalGradientRect(int left, int top, int right, int bottom, int startColor, int endColor)
    {
        drawVerticalGradientRect(left, top, right, bottom, startColor, endColor, 0);
    }

    /**
     * Draws a rectangle with a vertical gradient between with specified
     * colors
     */
    public static void drawVerticalGradientRect(int left, int top, int right, int bottom, int startColor, int endColor, float zLevel)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        float a1 = (startColor >> 24 & 255) / 255.0F;
        float r1 = (startColor >> 16 & 255) / 255.0F;
        float g1 = (startColor >> 8 & 255) / 255.0F;
        float b1 = (startColor & 255) / 255.0F;
        float a2 = (endColor >> 24 & 255) / 255.0F;
        float r2 = (endColor >> 16 & 255) / 255.0F;
        float g2 = (endColor >> 8 & 255) / 255.0F;
        float b2 = (endColor & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        vertex(buffer, right, top, zLevel, r1, g1, b1, a1);
        vertex(buffer, left, top, zLevel, r1, g1, b1, a1);
        vertex(buffer, left, bottom, zLevel, r2, g2, b2, a2);
        vertex(buffer, right, bottom, zLevel, r2, g2, b2, a2);
        tessellator.draw();

        RenderSystem.disableBlend();
    }

    /* Billboards (textured quads) */

    public static void drawBillboard(int x, int y, int u, int v, int w, int h, int textureW, int textureH)
    {
        drawBillboard(x, y, u, v, w, h, textureW, textureH, 0);
    }

    /**
     * Draw a textured quad with given UV, dimensions and custom texture size
     */
    public static void drawBillboard(int x, int y, int u, int v, int w, int h, int textureW, int textureH, float z)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        drawBillboard(buffer, x, y, u, v, w, h, textureW, textureH, z);
        tessellator.draw();
    }

    public static void drawBillboard(BufferBuilder buffer, int x, int y, int u, int v, int w, int h, int textureW, int textureH, float z)
    {
        float tw = 1F / textureW;
        float th = 1F / textureH;

        vertex(buffer, x, y + h, z, u * tw, (v + h) * th);
        vertex(buffer, x + w, y + h, z, (u + w) * tw, (v + h) * th);
        vertex(buffer, x + w, y, z, (u + w) * tw, v * th);
        vertex(buffer, x, y, z, u * tw, v * th);
    }

    public static void drawBillboard(int x, int y, int u, int v, int w, int h, int textureW, int textureH, int tu, int tv)
    {
        drawBillboard(x, y, u, v, w, h, textureW, textureH, tu, tv, 0);
    }

    /**
     * Draw a textured quad with given UV, dimensions and custom texture size
     */
    public static void drawBillboard(int x, int y, int u, int v, int w, int h, int textureW, int textureH, int tu, int tv, float z)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        drawBillboard(buffer, x, y, u, v, w, h, textureW, textureH, tu, tv, z);
        tessellator.draw();
    }

    public static void drawBillboard(BufferBuilder buffer, int x, int y, int u, int v, int w, int h, int textureW, int textureH, int tu, int tv, float z)
    {
        float tw = 1F / textureW;
        float th = 1F / textureH;

        vertex(buffer, x, y + h, z, u * tw, tv * th);
        vertex(buffer, x + w, y + h, z, tu * tw, tv * th);
        vertex(buffer, x + w, y, z, tu * tw, v * th);
        vertex(buffer, x, y, z, u * tw, v * th);
    }

    /* Borders and outlines */

    public static int drawBorder(Area area, int color)
    {
        if (!McLib.enableBorders.get())
        {
            area.draw(color);

            return 0;
        }

        area.draw(0xff000000);
        area.draw(color, 1);

        return 1;
    }

    public static void drawOutlineCenter(int x, int y, int offset, int color)
    {
        drawOutlineCenter(x, y, offset, color, 1);
    }

    public static void drawOutlineCenter(int x, int y, int offset, int color, int border)
    {
        drawOutline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public static void drawOutline(int left, int top, int right, int bottom, int color)
    {
        drawOutline(left, top, right, bottom, color, 1);
    }

    /**
     * Draw rectangle outline with given border
     */
    public static void drawOutline(int left, int top, int right, int bottom, int color, int border)
    {
        drawRect(left, top, left + border, bottom, color);
        drawRect(right - border, top, right, bottom, color);
        drawRect(left + border, top, right - border, top + border, color);
        drawRect(left + border, bottom - border, right - border, bottom, color);
    }

    public static void drawOutlinedIcon(Icon icon, int x, int y, int color)
    {
        drawOutlinedIcon(icon, x, y, color, 0F, 0F);
    }

    /**
     * Draw an icon with a black outline
     *
     * Legacy left {@code GlStateManager.color} tinted after the last render;
     * modern shader color is global state, so it is reset here (recorded
     * deviation).
     */
    public static void drawOutlinedIcon(Icon icon, int x, int y, int color, float ax, float ay)
    {
        RenderSystem.setShaderColor(0, 0, 0, 1);
        icon.render(x - 1, y, ax, ay);
        icon.render(x + 1, y, ax, ay);
        icon.render(x, y - 1, ax, ay);
        icon.render(x, y + 1, ax, ay);
        bindColor(color);
        icon.render(x, y, ax, ay);
        resetColor();
    }

    public static void drawLockedArea(GuiElement element)
    {
        drawLockedArea(element, 0);
    }

    /**
     * Generic method for drawing locked (disabled) state of
     * an input field
     */
    public static void drawLockedArea(GuiElement element, int padding)
    {
        if (!element.isEnabled())
        {
            element.area.draw(ColorUtils.HALF_BLACK, padding);

            GuiDraw.drawOutlinedIcon(Icons.LOCKED, element.area.mx(), element.area.my(), 0xffffffff, 0.5F, 0.5F);
        }
    }

    /* Drop shadows */

    public static void drawDropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        left -= offset;
        top -= offset;
        right += offset;
        bottom += offset;

        float a1 = (opaque >> 24 & 255) / 255.0F;
        float r1 = (opaque >> 16 & 255) / 255.0F;
        float g1 = (opaque >> 8 & 255) / 255.0F;
        float b1 = (opaque & 255) / 255.0F;
        float a2 = (shadow >> 24 & 255) / 255.0F;
        float r2 = (shadow >> 16 & 255) / 255.0F;
        float g2 = (shadow >> 8 & 255) / 255.0F;
        float b2 = (shadow & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Draw opaque part */
        vertex(buffer, right - offset, top + offset, 0, r1, g1, b1, a1);
        vertex(buffer, left + offset, top + offset, 0, r1, g1, b1, a1);
        vertex(buffer, left + offset, bottom - offset, 0, r1, g1, b1, a1);
        vertex(buffer, right - offset, bottom - offset, 0, r1, g1, b1, a1);

        /* Draw top shadow */
        vertex(buffer, right, top, 0, r2, g2, b2, a2);
        vertex(buffer, left, top, 0, r2, g2, b2, a2);
        vertex(buffer, left + offset, top + offset, 0, r1, g1, b1, a1);
        vertex(buffer, right - offset, top + offset, 0, r1, g1, b1, a1);

        /* Draw bottom shadow */
        vertex(buffer, right - offset, bottom - offset, 0, r1, g1, b1, a1);
        vertex(buffer, left + offset, bottom - offset, 0, r1, g1, b1, a1);
        vertex(buffer, left, bottom, 0, r2, g2, b2, a2);
        vertex(buffer, right, bottom, 0, r2, g2, b2, a2);

        /* Draw left shadow */
        vertex(buffer, left + offset, top + offset, 0, r1, g1, b1, a1);
        vertex(buffer, left, top, 0, r2, g2, b2, a2);
        vertex(buffer, left, bottom, 0, r2, g2, b2, a2);
        vertex(buffer, left + offset, bottom - offset, 0, r1, g1, b1, a1);

        /* Draw right shadow */
        vertex(buffer, right, top, 0, r2, g2, b2, a2);
        vertex(buffer, right - offset, top + offset, 0, r1, g1, b1, a1);
        vertex(buffer, right - offset, bottom - offset, 0, r1, g1, b1, a1);
        vertex(buffer, right, bottom, 0, r2, g2, b2, a2);

        tessellator.draw();

        RenderSystem.disableBlend();
    }

    public static void drawDropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
        float a1 = (opaque >> 24 & 255) / 255.0F;
        float r1 = (opaque >> 16 & 255) / 255.0F;
        float g1 = (opaque >> 8 & 255) / 255.0F;
        float b1 = (opaque & 255) / 255.0F;
        float a2 = (shadow >> 24 & 255) / 255.0F;
        float r2 = (shadow >> 16 & 255) / 255.0F;
        float g2 = (shadow >> 8 & 255) / 255.0F;
        float b2 = (shadow & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        vertex(buffer, x, y, 0, r1, g1, b1, a1);

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            vertex(buffer, (float) (x - Math.cos(a) * radius), (float) (y + Math.sin(a) * radius), 0, r2, g2, b2, a2);
        }

        tessellator.draw();

        RenderSystem.disableBlend();
    }

    public static void drawDropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        if (offset >= radius)
        {
            drawDropCircleShadow(x, y, radius, segments, opaque, shadow);

            return;
        }

        float a1 = (opaque >> 24 & 255) / 255.0F;
        float r1 = (opaque >> 16 & 255) / 255.0F;
        float g1 = (opaque >> 8 & 255) / 255.0F;
        float b1 = (opaque & 255) / 255.0F;
        float a2 = (shadow >> 24 & 255) / 255.0F;
        float r2 = (shadow >> 16 & 255) / 255.0F;
        float g2 = (shadow >> 8 & 255) / 255.0F;
        float b2 = (shadow & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        /* Draw opaque base */
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        vertex(buffer, x, y, 0, r1, g1, b1, a1);

        for (int i = 0; i <= segments; i ++)
        {
            double a = i / (double) segments * Math.PI * 2 - Math.PI / 2;

            vertex(buffer, (float) (x - Math.cos(a) * offset), (float) (y + Math.sin(a) * offset), 0, r1, g1, b1, a1);
        }

        tessellator.draw();

        /* Draw outer shadow */
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < segments; i ++)
        {
            double alpha1 = i / (double) segments * Math.PI * 2 - Math.PI / 2;
            double alpha2 = (i + 1) / (double) segments * Math.PI * 2 - Math.PI / 2;

            vertex(buffer, (float) (x - Math.cos(alpha2) * offset), (float) (y + Math.sin(alpha2) * offset), 0, r1, g1, b1, a1);
            vertex(buffer, (float) (x - Math.cos(alpha1) * offset), (float) (y + Math.sin(alpha1) * offset), 0, r1, g1, b1, a1);
            vertex(buffer, (float) (x - Math.cos(alpha1) * radius), (float) (y + Math.sin(alpha1) * radius), 0, r2, g2, b2, a2);
            vertex(buffer, (float) (x - Math.cos(alpha2) * radius), (float) (y + Math.sin(alpha2) * radius), 0, r2, g2, b2, a2);
        }

        tessellator.draw();

        RenderSystem.disableBlend();
    }

    /* Text */

    /**
     * Headless/test seam (P32+): when the font is null (no Minecraft client),
     * {@link #textWidth} consults this function so pure-layout logic (tooltip
     * anchoring, list rows, context-menu widths) stays testable. Defaults to
     * 0-width.
     */
    public static ToIntFunction<String> widthOverride;

    /**
     * Legacy {@code font.getStringWidth(...)} with a null-font (headless)
     * guard — all ported widgets measure text through this.
     */
    public static int textWidth(TextRenderer font, String text)
    {
        if (font == null)
        {
            return widthOverride == null ? 0 : widthOverride.applyAsInt(text);
        }

        return font.getWidth(text);
    }

    /**
     * Legacy {@code font.FONT_HEIGHT} with a null-font guard (vanilla's value
     * is 9 on every version).
     */
    public static int fontHeight(TextRenderer font)
    {
        return font == null ? 9 : font.fontHeight;
    }

    /**
     * Draw a string through the bound {@link DrawContext} — no-op headless.
     * (Legacy {@code FontRenderer.drawString(text, x, y, color, shadow)}.)
     *
     * <p>Two 1.20.4-only corrections, both required for the string to behave
     * like 1.12.2's:</p>
     *
     * <ul>
     * <li>drawn through a {@linkplain #flattenDepth z-flattened} matrix, so the
     * glyph layer lands on the GUI plane instead of 0.03 in front of it and
     * cannot punch a depth hole through whatever is drawn over it later;</li>
     * <li>{@linkplain #flush() flushed} on the spot, so the string is painted
     * where the call sits in the element draw order and under the scissor that
     * is active right now.</li>
     * </ul>
     */
    public static void drawString(TextRenderer font, String text, int x, int y, int color, boolean shadow)
    {
        if (drawContext != null && font != null)
        {
            MatrixStack matrices = drawContext.getMatrices();

            matrices.push();
            flattenDepth(matrices.peek().getPositionMatrix());
            drawContext.drawText(font, text, x, y, color, shadow);
            matrices.pop();

            flush();
        }
    }

    /** Legacy {@code FontRenderer.drawString(text, x, y, color)} (no shadow). */
    public static void drawString(TextRenderer font, String text, int x, int y, int color)
    {
        drawString(font, text, x, y, color, false);
    }

    /** Legacy {@code FontRenderer.drawStringWithShadow}. */
    public static void drawStringWithShadow(TextRenderer font, String text, int x, int y, int color)
    {
        drawString(font, text, x, y, color, true);
    }

    /** Legacy {@code Gui.drawCenteredString} (shadowed, centered on x). */
    public static void drawCenteredString(TextRenderer font, String text, int x, int y, int color)
    {
        drawString(font, text, x - textWidth(font, text) / 2, y, color, true);
    }

    public static int drawMultiText(TextRenderer font, String text, int x, int y, int color, int width)
    {
        return drawMultiText(font, text, x, y, color, width, 12);
    }

    public static int drawMultiText(TextRenderer font, String text, int x, int y, int color, int width, int lineHeight)
    {
        return drawMultiText(font, text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public static int drawMultiText(TextRenderer font, String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        List<String> list = listFormattedStringToWidth(text, width, s -> textWidth(font, s));
        int h = (lineHeight * (list.size() - 1)) + fontHeight(font);

        y -= h * ay;

        for (String string : list)
        {
            drawString(font, string, (int) (x + (width - textWidth(font, string)) * ax), y, color, true);

            y += lineHeight;
        }

        return h;
    }

    public static void drawTextBackground(TextRenderer font, String text, int x, int y, int color, int background)
    {
        drawTextBackground(font, text, x, y, color, background, 3);
    }

    public static void drawTextBackground(TextRenderer font, String text, int x, int y, int color, int background, int offset)
    {
        drawTextBackground(font, text, x, y, color, background, offset, true);
    }

    public static void drawTextBackground(TextRenderer font, String text, int x, int y, int color, int background, int offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            drawRect(x - offset, y - offset, x + textWidth(font, text) + offset, y + fontHeight(font) + offset, background);
        }

        drawString(font, text, x, y, color, shadow);
    }

    /* Word wrap — reimplementation of 1.12.2 FontRenderer's
     * listFormattedStringToWidth/wrapFormattedStringToWidth/sizeStringToWidth
     * (wraps on spaces, hard-breaks long words, honors '\n' and carries '§'
     * format codes to the next line), parametrized over a string-width
     * function so it is headless-testable. */

    public static List<String> listFormattedStringToWidth(String str, int wrapWidth, ToIntFunction<String> width)
    {
        List<String> list = new ArrayList<String>();

        for (String line : wrapFormattedStringToWidth(str, wrapWidth, width).split("\n"))
        {
            list.add(line);
        }

        return list;
    }

    public static String wrapFormattedStringToWidth(String str, int wrapWidth, ToIntFunction<String> width)
    {
        int i = sizeStringToWidth(str, wrapWidth, width);

        if (str.length() <= i)
        {
            return str;
        }

        String s = str.substring(0, i);
        char c0 = str.charAt(i);
        boolean flag = c0 == ' ' || c0 == '\n';
        String s1 = getFormatFromString(s) + str.substring(i + (flag ? 1 : 0));

        return s + "\n" + wrapFormattedStringToWidth(s1, wrapWidth, width);
    }

    private static int sizeStringToWidth(String str, int wrapWidth, ToIntFunction<String> width)
    {
        int i = str.length();
        int j = 0;
        int k = 0;
        int l = -1;

        for (boolean flag = false; k < i; ++k)
        {
            char c0 = str.charAt(k);

            switch (c0)
            {
                case '\n':
                    --k;
                    break;
                case ' ':
                    l = k;
                default:
                    j += width.applyAsInt(String.valueOf(c0));

                    if (flag)
                    {
                        ++j;
                    }

                    break;
                case '§':
                    if (k < i - 1)
                    {
                        ++k;
                        char c1 = str.charAt(k);

                        if (c1 != 'l' && c1 != 'L')
                        {
                            if (c1 == 'r' || c1 == 'R' || isFormatColor(c1))
                            {
                                flag = false;
                            }
                        }
                        else
                        {
                            flag = true;
                        }
                    }
            }

            if (c0 == '\n')
            {
                ++k;
                l = k;
                break;
            }

            if (j > wrapWidth)
            {
                break;
            }
        }

        return k != i && l != -1 && l < k ? l : k;
    }

    public static String getFormatFromString(String text)
    {
        String s = "";
        int i = -1;
        int j = text.length();

        while ((i = text.indexOf('§', i + 1)) != -1)
        {
            if (i < j - 1)
            {
                char c0 = text.charAt(i + 1);

                if (isFormatColor(c0))
                {
                    s = "§" + c0;
                }
                else if (isFormatSpecial(c0))
                {
                    s = s + "§" + c0;
                }
            }
        }

        return s;
    }

    private static boolean isFormatColor(char colorChar)
    {
        return colorChar >= '0' && colorChar <= '9' || colorChar >= 'a' && colorChar <= 'f' || colorChar >= 'A' && colorChar <= 'F';
    }

    private static boolean isFormatSpecial(char formatChar)
    {
        return formatChar >= 'k' && formatChar <= 'o' || formatChar >= 'K' && formatChar <= 'O' || formatChar == 'r' || formatChar == 'R';
    }

    /* Custom background */

    /**
     * User-configurable dashboard backdrop: {@code McLib.backgroundImage}
     * (stretched, tinted by {@code McLib.backgroundColor}) or a plain color
     * rect.
     */
    public static void drawCustomBackground(int x, int y, int width, int height)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        ResourceLocation background = McLib.backgroundImage.get();
        int color = McLib.backgroundColor.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (background == null)
        {
            drawRect(x, y, x + width, y + height, color);
        }
        else
        {
            RenderSystem.setShaderTexture(0, background.toIdentifier());
            bindColor(color);
            GuiDraw.drawBillboard(x, y, 0, 0, width, height, width, height);
            resetColor();
        }

        RenderSystem.disableBlend();
    }

    public static void drawRepeatBillboard(int x, int y, int w, int h, int u, int v, int tileW, int tileH, int tw, int th)
    {
        if (drawContext == null)
        {
            /* Headless (unit tests): GL entry points no-op (P31 contract) */
            return;
        }

        int countX = ((w - 1) / tileW) + 1;
        int countY = ((h - 1) / tileH) + 1;
        int fillerX = w - (countX - 1) * tileW;
        int fillerY = h - (countY - 1) * tileH;

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        for (int i = 0, c = countX * countY; i < c; i ++)
        {
            int ix = i % countX;
            int iy = i / countX;
            int xx = x + ix * tileW;
            int yy = y + iy * tileH;
            int xw = ix == countX - 1 ? fillerX : tileW;
            int yh = iy == countY - 1 ? fillerY : tileH;

            drawBillboard(buffer, xx, yy, u, v, xw, yh, tw, th, 0);
        }

        tessellator.draw();
    }
}
