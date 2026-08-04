package mchorse.mclib.client.gui.framework.elements.buttons;

import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Icon;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiIconElement} (roadmap P32). Legacy tinted
 * via {@code ColorUtils.bindColor} ({@code GlStateManager.color}); the port
 * uses {@code GuiDraw.bindColor}/{@code resetColor} — the reset is required
 * on 1.20.4 because the shader color is global state (P31 convention).
 */
public class GuiIconElement extends GuiClickElement<GuiIconElement>
{
    public Icon icon;
    public int iconColor = 0xffffffff;
    public Icon hoverIcon;
    public int hoverColor = 0xffaaaaaa;

    public int disabledColor = 0x80404040;

    public GuiIconElement(MinecraftClient mc, Icon icon, Consumer<GuiIconElement> callback)
    {
        super(mc, callback);

        this.icon = icon;
        this.hoverIcon = icon;
        this.flex().wh(20, 20);
    }

    public GuiIconElement both(Icon icon)
    {
        this.icon = this.hoverIcon = icon;

        return this;
    }

    public GuiIconElement icon(Icon icon)
    {
        this.icon = icon;

        return this;
    }

    public GuiIconElement hovered(Icon icon)
    {
        this.hoverIcon = icon;

        return this;
    }

    public GuiIconElement iconColor(int color)
    {
        this.iconColor = color;

        return this;
    }

    public GuiIconElement hoverColor(int color)
    {
        this.hoverColor = color;

        return this;
    }

    public GuiIconElement disabledColor(int color)
    {
        this.disabledColor = color;

        return this;
    }

    @Override
    protected GuiIconElement get()
    {
        return this;
    }

    @Override
    protected void drawSkin(GuiContext context)
    {
        Icon icon = this.hover ? this.hoverIcon : this.icon;
        int color = this.hover ? this.hoverColor : this.iconColor;

        if (this.isEnabled())
        {
            GuiDraw.bindColor(color);
            icon.render(this.area.mx(), this.area.my(), 0.5F, 0.5F);
        }
        else
        {
            GuiDraw.bindColor(this.disabledColor);
            icon.render(this.area.mx(), this.area.my(), 0.5F, 0.5F);
        }

        GuiDraw.resetColor();
    }
}
