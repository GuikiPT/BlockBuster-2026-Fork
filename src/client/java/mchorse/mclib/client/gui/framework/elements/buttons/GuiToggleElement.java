package mchorse.mclib.client.gui.framework.elements.buttons;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.framework.elements.utils.ITextColoring;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.config.values.ValueBoolean;
import mchorse.mclib.utils.ColorUtils;
import net.minecraft.client.MinecraftClient;

import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiToggleElement} (roadmap P32). Two render
 * modes switched by {@code McLib.enableCheckboxRendering}: the 11px checkbox
 * drawing a literal {@code "x"} glyph, or the 16×10 iOS-style switch with
 * gradient shading.
 */
public class GuiToggleElement extends GuiClickElement<GuiToggleElement> implements ITextColoring
{
    public IKey label;
    public int color = 0xffffff;
    public boolean textShadow = true;
    private boolean state;

    public GuiToggleElement(MinecraftClient mc, ValueBoolean value)
    {
        this(mc, value, null);
    }

    public GuiToggleElement(MinecraftClient mc, ValueBoolean value, @Nullable Consumer<GuiToggleElement> callback)
    {
        this(mc, IKey.lang(value.getLabelKey()), value.get(), callback == null ? (toggle) -> value.set(toggle.isToggled()) : (toggle) ->
        {
            value.set(toggle.isToggled());
            callback.accept(toggle);
        });
        this.tooltip(IKey.lang(value.getCommentKey()));
    }

    public GuiToggleElement(MinecraftClient mc, IKey label, @Nullable Consumer<GuiToggleElement> callback)
    {
        this(mc, label, false, callback);
    }

    public GuiToggleElement(MinecraftClient mc, IKey label, boolean state, @Nullable Consumer<GuiToggleElement> callback)
    {
        super(mc, callback);

        this.label = label;
        this.state = state;
        this.flex().h(14);
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.color(color, shadow);
    }

    public GuiToggleElement label(IKey label)
    {
        this.label = label;

        return this;
    }

    public GuiToggleElement toggled(boolean state)
    {
        this.state = state;

        return this;
    }

    public GuiToggleElement color(int color)
    {
        return this.color(color, true);
    }

    public GuiToggleElement color(int color, boolean textShadow)
    {
        this.color = color;
        this.textShadow = textShadow;

        return this;
    }

    public boolean isToggled()
    {
        return this.state;
    }

    @Override
    protected void click(int mouseWheel)
    {
        this.state = !this.state;

        super.click(mouseWheel);
    }

    @Override
    protected GuiToggleElement get()
    {
        return this;
    }

    @Override
    protected void drawSkin(GuiContext context)
    {
        if (McLib.enableCheckboxRendering.get())
        {
            int y = this.area.my(GuiDraw.fontHeight(this.font) - 1);

            GuiDraw.drawRect(this.area.x, y - 3, this.area.x + 11, y + 8, 0xff000000 + McLib.primaryColor.get());

            if (McLib.enableBorders.get())
            {
                GuiDraw.drawOutline(this.area.x, y - 3, this.area.x + 11, y + 8, 0xff000000);
            }

            if (this.state)
            {
                GuiDraw.drawString(this.font, "x", this.area.x + 3, y - 2, 0xffffff, this.textShadow);
            }

            GuiDraw.drawString(this.font, this.label.get(), this.area.x + 14, y, this.color, this.textShadow);

            if (!this.isEnabled())
            {
                GuiDraw.drawRect(this.area.x, y - 3, this.area.x + 11, y + 8, ColorUtils.HALF_BLACK);
                GuiDraw.drawOutlinedIcon(Icons.LOCKED, this.area.x + 5, y + 2, 0xffffffff, 0.5F, 0.5F);
            }
        }
        else
        {
            GuiDraw.drawString(this.font, this.label.get(), this.area.x, this.area.my(GuiDraw.fontHeight(this.font) - 1), this.color, this.textShadow);

            /* Draw toggle switch */
            int w = 16;
            int h = 10;
            int x = this.area.ex() - w - 2;
            int y = this.area.my();
            int color = McLib.primaryColor.get();

            if (this.hover)
            {
                color = ColorUtils.multiplyColor(color, 0.85F);
            }

            /* Draw toggle background */
            GuiDraw.drawRect(x, y - h / 2, x + w, y - h / 2 + h, 0xff000000);
            GuiDraw.drawRect(x + 1, y - h / 2 + 1, x + w - 1, y - h / 2 + h - 1, 0xff000000 + (this.state ? color : (this.hover ? 0x3a3a3a : 0x444444)));

            if (this.state)
            {
                GuiDraw.drawHorizontalGradientRect(x + 1, y - h / 2 + 1, x + w / 2, y - h / 2 + h - 1, 0x66ffffff, 0x00ffffff);
            }
            else
            {
                GuiDraw.drawHorizontalGradientRect(x + w / 2, y - h / 2 + 1, x + w - 1, y - h / 2 + h - 1, 0x00000000, 0x66000000);
            }

            if (!this.isEnabled())
            {
                GuiDraw.drawRect(x, y - h / 2, x + w, y - h / 2 + h, ColorUtils.HALF_BLACK);
            }

            x += this.state ? w - 2 : 2;

            /* Draw toggle switch */
            GuiDraw.drawRect(x - 4, y - 8, x + 4, y + 8, 0xff000000);
            GuiDraw.drawRect(x - 3, y - 7, x + 3, y + 7, 0xffffffff);
            GuiDraw.drawRect(x - 2, y - 6, x + 3, y + 7, 0xff888888);
            GuiDraw.drawRect(x - 2, y - 6, x + 2, y + 6, 0xffbbbbbb);

            if (!this.isEnabled())
            {
                GuiDraw.drawRect(x - 4, y - 8, x + 4, y + 8, ColorUtils.HALF_BLACK);

                GuiDraw.drawOutlinedIcon(Icons.LOCKED, this.area.ex() - w / 2 - 2, y, 0xffffffff, 0.5F, 0.5F);
            }
        }
    }
}
