package mchorse.mclib.client.gui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.utils.resources.ResourceLocation;

/**
 * Port of McLib 2.4.3's {@code Icon} (roadmap P31). The location keeps the
 * bundled {@link ResourceLocation} type (legacy signature); conversion to a
 * vanilla {@code Identifier} happens at bind time. Legacy
 * {@code GlStateManager.enableAlpha} pairs disappear — the core-profile
 * position-tex shader handles alpha via blending.
 */
public class Icon
{
    public final ResourceLocation location;
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public int textureW = 256;
    public int textureH = 256;

    public Icon(ResourceLocation location, int x, int y)
    {
        this(location, x, y, 16, 16);
    }

    public Icon(ResourceLocation location, int x, int y, int w, int h)
    {
        this.location = location;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Icon(ResourceLocation location, int x, int y, int w, int h, int textureW, int textureH)
    {
        this(location, x, y, w, h);
        this.textureW = textureW;
        this.textureH = textureH;
    }

    public void render(int x, int y)
    {
        this.render(x, y, 0, 0);
    }

    public void render(int x, int y, float ax, float ay)
    {
        if (this.location == null || GuiDraw.getDrawContext() == null)
        {
            return;
        }

        x -= ax * this.w;
        y -= ay * this.h;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, this.location.toIdentifier());
        GuiDraw.drawBillboard(x, y, this.x, this.y, this.w, this.h, this.textureW, this.textureH);
        RenderSystem.disableBlend();
    }

    public void renderArea(int x, int y, int w, int h)
    {
        if (this.location == null || GuiDraw.getDrawContext() == null)
        {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, this.location.toIdentifier());
        GuiDraw.drawRepeatBillboard(x, y, w, h, this.x, this.y, this.w, this.h, this.textureW, this.textureH);
        RenderSystem.disableBlend();
    }
}
