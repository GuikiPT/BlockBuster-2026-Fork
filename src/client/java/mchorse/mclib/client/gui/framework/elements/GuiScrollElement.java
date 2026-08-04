package mchorse.mclib.client.gui.framework.elements;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.IViewportStack;
import mchorse.mclib.client.gui.utils.ScrollArea;
import mchorse.mclib.client.gui.utils.ScrollDirection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Scroll area GUI class
 *
 * This bad boy allows to scroll stuff.
 *
 * (Port of McLib 2.4.3's {@code GuiScrollElement}, roadmap P37. The legacy
 * {@code GlStateManager.translate} of the contents becomes a push/translate/
 * pop on the frame's {@code DrawContext} matrix stack — P31 conventions;
 * mouse coordinates are shifted through {@code apply}/{@code unapply}
 * exactly like legacy.)
 */
public class GuiScrollElement extends GuiElement implements IViewport
{
    public ScrollArea scroll;

    public GuiScrollElement(MinecraftClient mc)
    {
        this(mc, ScrollDirection.VERTICAL);
    }

    public GuiScrollElement(MinecraftClient mc, ScrollDirection direction)
    {
        super(mc);

        this.area = this.scroll = new ScrollArea(0);
        this.scroll.direction = direction;
        this.scroll.scrollSpeed = 20;
    }

    public GuiScrollElement cancelScrollEdge()
    {
        this.scroll.cancelScrollEdge = true;

        return this;
    }

    @Override
    public void apply(IViewportStack stack)
    {
        stack.pushViewport(this.area);

        if (this.scroll.direction == ScrollDirection.VERTICAL)
        {
            stack.shiftY(this.scroll.scroll);
        }
        else
        {
            stack.shiftX(this.scroll.scroll);
        }
    }

    @Override
    public void unapply(IViewportStack stack)
    {
        if (this.scroll.direction == ScrollDirection.VERTICAL)
        {
            stack.shiftY(-this.scroll.scroll);
        }
        else
        {
            stack.shiftX(-this.scroll.scroll);
        }

        stack.popViewport();
    }

    @Override
    public void resize()
    {
        super.resize();

        this.scroll.clamp();
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        this.apply(context);
        boolean result = super.mouseClicked(context);
        this.unapply(context);

        if (!this.area.isInside(context))
        {
            if (context.isFocused() && this.isDescendant((GuiElement) context.activeElement))
            {
                context.unfocus();
            }

            return false;
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        return result;
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        if (!this.area.isInside(context))
        {
            if (context.isFocused() && this.isDescendant((GuiElement) context.activeElement))
            {
                context.unfocus();
            }

            return false;
        }

        this.apply(context);
        boolean result = super.mouseScrolled(context);
        this.unapply(context);

        if (result)
        {
            return true;
        }

        return this.scroll.mouseScroll(context);
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        this.scroll.mouseReleased(context);

        this.apply(context);
        super.mouseReleased(context);
        this.unapply(context);
    }

    @Override
    public void draw(GuiContext context)
    {
        GuiElement lastTooltip = context.tooltip.element;
        DrawContext drawContext = GuiDraw.getDrawContext();

        this.scroll.drag(context.mouseX, context.mouseY);

        GuiDraw.scissor(this.scroll.x, this.scroll.y, this.scroll.w, this.scroll.h, context);

        if (drawContext != null)
        {
            drawContext.getMatrices().push();

            /* Translate the contents (scroll) — legacy GlStateManager.translate */
            if (this.scroll.direction == ScrollDirection.VERTICAL)
            {
                drawContext.getMatrices().translate(0, -this.scroll.scroll, 0);
            }
            else
            {
                drawContext.getMatrices().translate(-this.scroll.scroll, 0, 0);
            }
        }

        this.apply(context);
        this.preDraw(context);

        super.draw(context);

        this.postDraw(context);
        this.unapply(context);

        if (drawContext != null)
        {
            drawContext.getMatrices().pop();
        }

        this.scroll.drawScrollbar();

        GuiDraw.unscissor(context);

        /* Clear tooltip in case if it was set outside of scroll area within the scroll */
        if (!this.area.isInside(context) && context.tooltip.element != lastTooltip)
        {
            context.tooltip.set(context, null);
        }
    }

    protected void preDraw(GuiContext context)
    {}

    protected void postDraw(GuiContext context)
    {}
}
