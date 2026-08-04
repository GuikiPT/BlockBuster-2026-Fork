package mchorse.mclib.client.gui.framework.elements.keyframes;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Scale;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.keyframes.Keyframe;
import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's abstract {@code GuiKeyframeElement} (roadmap P43) —
 * the shared input model of the keyframe editors: selection state, drag
 * modes (slide/move/scroll/grab), zoom-around-cursor math and the dimming of
 * out-of-duration regions.
 *
 * <p>1.20.4 boundary decisions:</p>
 * <ul>
 * <li>{@code GuiScreen.isShift/Alt/CtrlKeyDown} → {@link GuiUtils} (headless
 * seam: {@code GuiUtils.keyDownOverride});</li>
 * <li>{@code Minecraft.IS_RUNNING_ON_MAC} → {@code
 * MinecraftClient.IS_SYSTEM_MAC} — the inverted scroll direction on Mac is
 * by design, do not "fix";</li>
 * <li>GL1 immediate mode → {@code DEBUG_LINES}/{@code QUADS} +
 * POSITION_COLOR (P38 idiom); the whole GL block no-ops headless (no bound
 * {@code DrawContext}), while {@link #handleMouse} (the drag logic) still
 * runs so tests can drive dragging through {@code draw()}.</li>
 * </ul>
 */
public abstract class GuiKeyframeElement extends GuiElement
{
    public static final Color COLOR = new Color();

    public Consumer<Keyframe> callback;
    public Selection which = Selection.NOT_SELECTED;
    public int duration;

    public double minZoom = 0.01D;
    public double maxZoom = 1000D;

    /**
     * Sliding flag, whether keyframes should be sorted after
     * dragging keyframes around
     */
    public boolean sliding;

    /**
     * Dragging flag, whether dragging got initiated (it might be possible
     * that there are 0 keyframes selected)
     */
    public boolean dragging;

    /**
     * Moving flag, whether the user dragged 3 pixels away from the original
     * place (also could have 0 keyframes selected)
     */
    protected boolean moving;

    /**
     * Scrolling flag, whether the user was navigating by dragging with
     * middle mouse held
     */
    protected boolean scrolling;

    /**
     * Grabbing flag, whether the user selected an area with Shift + click dragging
     * in order to select multiple keyframes
     */
    protected boolean grabbing;

    protected int lastX;
    protected int lastY;
    protected double lastT;
    protected double lastV;

    protected Scale scaleX;

    protected IAxisConverter converter;

    public GuiKeyframeElement(MinecraftClient mc, Consumer<Keyframe> callback)
    {
        super(mc);

        this.callback = callback;
        this.scaleX = new Scale(this.area, false);
        this.scaleX.anchor(0.5F);
    }

    public void setConverter(IAxisConverter converter)
    {
        this.converter = converter;
    }

    public Scale getScaleX()
    {
        return this.scaleX;
    }

    protected void setKeyframe(Keyframe current)
    {
        if (this.callback != null)
        {
            this.callback.accept(current);
        }
    }

    /* Setters */

    public abstract void setTick(double tick, boolean opposite);

    public abstract void setValue(double value, boolean opposite);

    public abstract void setInterpolation(KeyframeInterpolation interp);

    public abstract void setEasing(KeyframeEasing easing);

    public void setDuration(long duration)
    {
        this.duration = (int) duration;
    }

    /* Graphing code */

    public abstract void resetView();

    public int toGraphX(double tick)
    {
        return (int) (this.scaleX.to(tick));
    }

    public double fromGraphX(int mouseX)
    {
        return this.scaleX.from(mouseX);
    }

    /* Abstract methods */

    public abstract Keyframe getCurrent();

    public abstract List<GuiSheet> getSheets();

    public abstract GuiSheet getSheet(int mouseY);

    public boolean isGrabbing()
    {
        return this.dragging && this.moving && this.grabbing;
    }

    public void selectByDuration(long duration)
    {}

    public abstract void selectAll();

    public abstract int getSelectedCount();

    public boolean isMultipleSelected()
    {
        return this.getSelectedCount() > 1;
    }

    public boolean hasSelected()
    {
        return this.getSelectedCount() > 0;
    }

    public abstract void clearSelection();

    public void doubleClick(int mouseX, int mouseY)
    {
        if (this.which == Selection.NOT_SELECTED)
        {
            this.addCurrent(mouseX, mouseY);
        }
        else if (this.which == Selection.KEYFRAME && !this.isMultipleSelected())
        {
            this.removeCurrent();
        }
    }

    public abstract void addCurrent(int mouseX, int mouseY);

    public abstract void removeCurrent();

    public abstract void removeSelectedKeyframes();

    /* Common hooks */

    protected void updateMoved()
    {}

    protected void moveNoKeyframe(GuiContext context, Keyframe frame, double x, double y)
    {}

    protected void drawCursor(GuiContext context)
    {}

    /* Mouse input handling */

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        if (super.mouseClicked(context))
        {
            return true;
        }

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        /* Select current point with a mouse click */
        if (this.area.isInside(mouseX, mouseY))
        {
            if (context.mouseButton == 0)
            {
                boolean shift = GuiUtils.isShiftKeyDown();

                /* Duplicate the keyframe */
                if (GuiUtils.isAltKeyDown() && !shift && this.which == Selection.KEYFRAME)
                {
                    this.duplicateKeyframe(context, mouseX, mouseY);

                    return false;
                }

                this.lastX = mouseX;
                this.lastY = mouseY;

                if (shift)
                {
                    this.grabbing = true;
                }

                if (!this.pickKeyframe(context, mouseX, mouseY, shift) && !shift)
                {
                    this.clearSelection();
                    this.setKeyframe(null);
                }

                this.dragging = true;
                this.pickedKeyframe(this.getSelectedCount());
            }
            else if (context.mouseButton == 2)
            {
                this.setupScrolling(context, mouseX, mouseY);
            }
        }

        return false;
    }

    protected void pickedKeyframe(int amount)
    {}

    protected abstract void duplicateKeyframe(GuiContext context, int mouseX, int mouseY);

    protected abstract boolean pickKeyframe(GuiContext context, int mouseX, int mouseY, boolean multi);

    protected void setupScrolling(GuiContext context, int mouseX, int mouseY)
    {
        this.scrolling = true;
        this.lastX = mouseX;
        this.lastY = mouseY;
        this.lastT = this.scaleX.getShift();
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        if (super.mouseScrolled(context))
        {
            return true;
        }

        if (this.area.isInside(context.mouseX, context.mouseY) && !this.scrolling)
        {
            int scroll = context.mouseWheel;

            if (!MinecraftClient.IS_SYSTEM_MAC)
            {
                scroll = -scroll;
            }

            this.zoom(scroll, context.mouseX, context.mouseY);

            return true;
        }

        return false;
    }

    protected void zoom(int scroll, int mouseX, int mouseY)
    {
        this.scaleX.setShift(this.scaleX.from(mouseX));
        this.scaleX.zoom(Math.copySign(this.scaleX.getZoomFactor(), scroll), this.minZoom, this.maxZoom);
        this.scaleX.setShift(2 * this.scaleX.getShift() - this.scaleX.from(mouseX));
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        super.mouseReleased(context);

        if (this.which == Selection.KEYFRAME)
        {
            if (this.sliding)
            {
                this.postSlideSort(context);
            }

            if (this.moving)
            {
                this.updateMoved();
            }
        }

        this.resetMouseReleased(context);
    }

    protected void postSlideSort(GuiContext context)
    {}

    protected void resetMouseReleased(GuiContext context)
    {
        this.grabbing = false;
        this.dragging = false;
        this.moving = false;
        this.scrolling = false;
    }

    /* Rendering */

    @Override
    public void draw(GuiContext context)
    {
        this.handleMouse(context, context.mouseX, context.mouseY);
        this.drawBackground(context);

        GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);

        this.drawGrid(context);
        this.drawCursor(context);

        /* Draw graph of the keyframe channel (GL block — no-op headless;
         * legacy: glLineWidth(guiScale * 1.5), disableTexture2D, blend) */
        if (GuiDraw.getDrawContext() != null)
        {
            float scale = this.mc == null ? 1F : (float) this.mc.getWindow().getScaleFactor();

            RenderSystem.lineWidth(scale * 1.5F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }

        this.drawGraph(context, context.mouseX, context.mouseY);

        /* Draw selection box */
        if (this.isGrabbing())
        {
            GuiDraw.drawRect(this.lastX, this.lastY, context.mouseX, context.mouseY, 0x440088ff);
        }

        if (GuiDraw.getDrawContext() != null)
        {
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1F);
        }

        GuiDraw.unscissor(context);

        super.draw(context);
    }

    protected void drawBackground(GuiContext context)
    {
        this.area.draw(ColorUtils.HALF_BLACK);

        if (this.duration > 0)
        {
            int leftBorder = this.toGraphX(0);
            int rightBorder = this.toGraphX(this.duration);

            if (leftBorder > this.area.x) GuiDraw.drawRect(this.area.x, this.area.y, leftBorder, this.area.y + this.area.h, ColorUtils.HALF_BLACK);
            if (rightBorder < this.area.ex()) GuiDraw.drawRect(rightBorder, this.area.y, this.area.ex(), this.area.y + this.area.h, ColorUtils.HALF_BLACK);
        }
    }

    protected void drawGrid(GuiContext context)
    {
        /* Draw scaling grid */
        int mult = this.scaleX.getMult();
        int hx = this.duration / mult;
        int ht = (int) this.fromGraphX(this.area.x);

        for (int j = Math.max(ht / mult, 0); j <= hx; j++)
        {
            int x = this.toGraphX(j * mult);

            if (x >= this.area.ex())
            {
                break;
            }

            String label = this.converter == null ? String.valueOf(j * mult) : this.converter.format(j * mult);

            GuiDraw.drawRect(x, this.area.y, x + 1, this.area.ey(), 0x44ffffff);
            GuiDraw.drawString(this.font, label, x + 4, this.area.y + 4, 0xffffff);
        }
    }

    protected abstract void drawGraph(GuiContext context, int mouseX, int mouseY);

    /**
     * Legacy signature kept ({@code pos().color().endVertex()} →
     * {@code vertex().color().next()}); the position matrix comes from the
     * frame's bound {@link DrawContext} like all P31 drawing.
     */
    protected void drawRect(BufferBuilder builder, int x, int y, int offset, int c)
    {
        COLOR.set(c, false);

        Matrix4f m = matrix();

        builder.vertex(m, x - offset, y + offset, 0F).color(COLOR.r, COLOR.g, COLOR.b, 1F).next();
        builder.vertex(m, x + offset, y + offset, 0F).color(COLOR.r, COLOR.g, COLOR.b, 1F).next();
        builder.vertex(m, x + offset, y - offset, 0F).color(COLOR.r, COLOR.g, COLOR.b, 1F).next();
        builder.vertex(m, x - offset, y - offset, 0F).color(COLOR.r, COLOR.g, COLOR.b, 1F).next();
    }

    /* GL helpers shared by the concrete editors (P43; no legacy equivalent —
     * legacy used raw Tessellator statics inline) */

    protected static Matrix4f matrix()
    {
        DrawContext context = GuiDraw.getDrawContext();

        return context == null ? new Matrix4f() : context.getMatrices().peek().getPositionMatrix();
    }

    protected static BufferBuilder beginLines()
    {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        return builder;
    }

    protected static BufferBuilder beginQuads()
    {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        return builder;
    }

    protected static void flush()
    {
        Tessellator.getInstance().draw();
    }

    /* Handling dragging */

    protected void handleMouse(GuiContext context, int mouseX, int mouseY)
    {
        if (this.dragging && !this.moving && (Math.abs(this.lastX - mouseX) > 3 || Math.abs(this.lastY - mouseY) > 3))
        {
            this.moving = true;
            this.sliding = true;
        }

        if (this.scrolling)
        {
            this.scrolling(mouseX, mouseY);
        }
        /* Move the current keyframe */
        else if (this.moving && !this.grabbing)
        {
            this.setKeyframe(this.moving(context, mouseX, mouseY));
            this.keepMoving();
        }
    }

    protected void keepMoving()
    {}

    protected void scrolling(int mouseX, int mouseY)
    {
        this.scaleX.setShift(-(mouseX - this.lastX) / this.scaleX.getZoom() + this.lastT);
    }

    protected Keyframe moving(GuiContext context, int mouseX, int mouseY)
    {
        return null;
    }
}
