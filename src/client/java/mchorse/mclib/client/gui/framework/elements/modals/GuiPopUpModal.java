package mchorse.mclib.client.gui.framework.elements.modals;

import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.Interpolations;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.Timer;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Port of McLib 2.4.3's {@code GuiPopUpModal} (roadmap P36) — a
 * cursor-anchored popup that starts fading (default 300ms lerp) once the
 * mouse leaves, cancels the fade if the mouse returns before expiry, and
 * removes itself via {@code context.postRenderCallbacks}.
 *
 * Boundary note: legacy clamped against
 * {@code Minecraft.getMinecraft().currentScreen.width/height} — the port
 * reads {@code context.screen.width/height} (same values, headless-safe).
 */
public class GuiPopUpModal extends GuiModal
{
    private Color backgroundColorDefault = new Color(0F, 0F, 0F, 0F);
    private Color textColorDefault = new Color(1F, 1F, 1F, 0F);
    private float shadowAlphaDefault = 0.26666668F;
    private float alphaDefault = 1F;

    private boolean init = false;
    private int x0;
    private int y0;
    private Timer timer;

    /* Colours */
    private float shadowAlpha;
    private float textAlpha;
    private float backgroundAlpha;

    private Color shadowColor;
    private Color backgroundColor;
    private Color textColor;

    /**
     * fade duration in milliseconds
     */
    private int duration = 300;

    public GuiPopUpModal(MinecraftClient mc, IKey label)
    {
        super(mc, label);

        this.defaultColors();
    }

    /**
     * Set the duration for the fading animation if it hadn't already begun
     *
     * @param duration in milliseconds
     */
    public void setFadeDuration(int duration)
    {
        if (this.timer == null)
        {
            this.duration = duration;
        }
    }

    @Override
    public void draw(GuiContext context)
    {
        if (!this.init)
        {
            this.x0 = context.mouseX() - this.area.w / 4;
            this.y0 = context.mouseY() - this.area.h / 2;

            this.x0 = this.x0 < 0 ? 0 : this.x0;
            this.y0 = this.y0 < 0 ? 0 : this.y0;

            if (context.screen.width < this.x0 + this.area.w)
            {
                this.x0 = context.screen.width - this.area.w;
            }

            if (context.screen.height < this.y0 + this.area.h)
            {
                this.y0 = context.screen.height - this.area.h;
            }

            this.init = true;
        }

        this.area.x = this.x0;
        this.area.y = this.y0;

        int shadowAlpha = (int) (this.shadowAlpha * 255F) << 24;
        int textAlpha = (int) (this.textAlpha * 255F) << 24;
        int backgroundAlpha = (int) (this.backgroundAlpha * 255F) << 24;

        GuiDraw.drawDropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 6, shadowAlpha + this.shadowColor.getRGBColor(), this.shadowColor.getRGBColor());
        GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), backgroundAlpha + this.backgroundColor.getRGBAColor());

        this.y = 0;

        List<String> lines = GuiDraw.listFormattedStringToWidth(this.label.get(), this.area.w - 20, (s) -> GuiDraw.textWidth(this.font, s));

        for (String line : lines)
        {
            GuiDraw.drawStringWithShadow(this.font, line, this.area.x + 10, this.area.y + 10 + this.y, textAlpha + this.textColor.getRGBAColor());

            this.y += 11;
        }

        if (!this.area.isInside(context))
        {
            if (this.timer == null)
            {
                this.timer = new Timer(this.duration);

                this.timer.mark();
            }

            float x = ((float) this.duration - this.timer.getRemaining()) / this.duration;

            this.backgroundAlpha = this.fadeAlpha(this.alphaDefault, 0F, x);
            this.textAlpha = this.fadeAlpha(this.alphaDefault, 0F, x);
            this.shadowAlpha = this.fadeAlpha(this.shadowAlphaDefault, 0F, MathUtils.clamp(x * 1.7F, 0, 1));

            if (this.timer.check())
            {
                context.postRenderCallbacks.add((c) ->
                {
                    this.removeFromParent();
                });
            }
        }
        else if (this.timer != null && !this.timer.check()) //if the mouse came back before the timer ended
        {
            this.timer = null;

            this.defaultColors();
        }
    }

    public void defaultColors()
    {
        this.shadowAlpha = this.shadowAlphaDefault;
        this.textAlpha = this.alphaDefault;
        this.backgroundAlpha = this.alphaDefault;

        this.shadowColor = new Color(McLib.primaryColor.get());
        this.backgroundColor = this.backgroundColorDefault.copy();
        this.textColor = this.textColorDefault.copy();
    }

    private float fadeAlpha(float a, float b, float x)
    {
        return Interpolations.lerp(a, b, x);
    }
}
