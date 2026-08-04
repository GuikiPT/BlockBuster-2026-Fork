package mchorse.mclib.client.gui.framework.elements.input.multiskin;

import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiCanvasEditor;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.resources.FilteredResourceLocation;
import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiMultiSkinEditor} (roadmap P40) — the
 * canvas-based per-layer multi-skin filter editor. Every control mutates the
 * selected {@link FilteredResourceLocation}'s fields directly, exactly like
 * legacy.
 *
 * <p>Deviations:</p>
 * <ul>
 * <li>Texture binding/size queries go through the
 * {@link GuiTexturePicker#textureBinder} seam (legacy:
 * {@code renderEngine.bindTexture} + {@code glGetTexLevelParameteri});
 * with no binder installed the canvas simply doesn't render and
 * {@code setSize} is skipped for 0×0.</li>
 * <li>The legacy GLSL preview shader ({@code /assets/mclib/shaders/preview
 * .vert/.frag} with pixelate/erase uniforms) is NOT ported — S7's
 * {@code TextureProcessor} produces CPU-composited previews that are
 * correct by construction; until then pixelate/erase edits are applied to
 * the data but not previewed. The static shader-handle fields are gone with
 * it.</li>
 * </ul>
 */
public class GuiMultiSkinEditor extends GuiCanvasEditor
{
    public GuiTexturePicker picker;
    public FilteredResourceLocation location;

    public GuiToggleElement autoSize;
    public GuiTrackpadElement sizeW;
    public GuiTrackpadElement sizeH;

    public GuiColorElement color;
    public GuiTrackpadElement scale;
    public GuiToggleElement scaleToLargest;
    public GuiTrackpadElement shiftX;
    public GuiTrackpadElement shiftY;

    public GuiTrackpadElement pixelate;
    public GuiToggleElement erase;

    public GuiMultiSkinEditor(MinecraftClient mc, GuiTexturePicker picker)
    {
        super(mc);

        this.picker = picker;

        this.autoSize = new GuiToggleElement(mc, IKey.lang("mclib.gui.multiskin.auto_size"), (toggle) ->
        {
            this.location.autoSize = toggle.isToggled();
            this.resizeCanvas();
        });
        this.autoSize.tooltip(IKey.lang("mclib.gui.multiskin.auto_size_tooltip"));
        this.sizeW = new GuiTrackpadElement(mc, (value) ->
        {
            this.location.sizeW = value.intValue();
            this.resizeCanvas();
        });
        this.sizeW.integer().limit(0).tooltip(IKey.lang("mclib.gui.multiskin.size_w"));
        this.sizeH = new GuiTrackpadElement(mc, (value) ->
        {
            this.location.sizeH = value.intValue();
            this.resizeCanvas();
        });
        this.sizeH.integer().limit(0).tooltip(IKey.lang("mclib.gui.multiskin.size_h"));

        this.color = new GuiColorElement(mc, (value) -> this.location.color = value);
        this.color.picker.editAlpha();
        this.color.direction(Direction.TOP).tooltip(IKey.lang("mclib.gui.multiskin.color"));
        this.scale = new GuiTrackpadElement(mc, (value) -> this.location.scale = value.floatValue());
        this.scale.limit(0).metric();
        this.scaleToLargest = new GuiToggleElement(mc, IKey.lang("mclib.gui.multiskin.scale_to_largest"), (toggle) -> this.location.scaleToLargest = toggle.isToggled());
        this.shiftX = new GuiTrackpadElement(mc, (value) -> this.location.shiftX = value.intValue());
        this.shiftX.integer();
        this.shiftY = new GuiTrackpadElement(mc, (value) -> this.location.shiftY = value.intValue());
        this.shiftY.integer();

        this.pixelate = new GuiTrackpadElement(mc, (value) -> this.location.pixelate = value.intValue());
        this.pixelate.integer().limit(1);
        this.erase = new GuiToggleElement(mc, IKey.lang("mclib.gui.multiskin.erase"), (toggle) -> this.location.erase = toggle.isToggled());
        this.erase.tooltip(IKey.lang("mclib.gui.multiskin.erase_tooltip"), Direction.TOP);

        this.editor.add(this.color);
        this.editor.add(Elements.label(IKey.lang("mclib.gui.multiskin.scale")).background(), this.scale, this.scaleToLargest);
        this.editor.add(Elements.label(IKey.lang("mclib.gui.multiskin.shift")).background(), this.shiftX, this.shiftY);
        this.editor.add(Elements.label(IKey.lang("mclib.gui.multiskin.pixelate")).background(), this.pixelate, this.erase);
        this.editor.add(Elements.label(IKey.lang("mclib.gui.multiskin.custom_size")).background(), this.autoSize, this.sizeW, this.sizeH);
    }

    public void resetView()
    {
        int w = 0;
        int h = 0;

        for (FilteredResourceLocation child : this.picker.multiRL.children)
        {
            w = Math.max(w, child.getWidth(this.textureWidth(child)));
            h = Math.max(h, child.getHeight(this.textureHeight(child)));
        }

        if (w > 0 && h > 0)
        {
            this.setSize(w, h);
        }

        this.color.picker.removeFromParent();
    }

    private void resizeCanvas()
    {
        int w = 0;
        int h = 0;

        for (FilteredResourceLocation child : this.picker.multiRL.children)
        {
            w = Math.max(w, child.getWidth(this.textureWidth(child)));
            h = Math.max(h, child.getHeight(this.textureHeight(child)));
        }

        if (w > 0 && h > 0 && (w != this.getWidth() || h != this.getHeight()))
        {
            this.setSize(w, h);
        }
    }

    /**
     * Legacy {@code bindTexture + glGetTexLevelParameteri(GL_TEXTURE_WIDTH)}
     * through the P40 binder seam; 0 when no binder/texture is available.
     */
    private int textureWidth(FilteredResourceLocation child)
    {
        GuiTexturePicker.TextureBinder binder = GuiTexturePicker.textureBinder;

        try
        {
            if (binder != null && child.path != null)
            {
                binder.bind(child.path);

                return binder.getWidth();
            }
        }
        catch (Exception e)
        {}

        return 0;
    }

    private int textureHeight(FilteredResourceLocation child)
    {
        GuiTexturePicker.TextureBinder binder = GuiTexturePicker.textureBinder;

        try
        {
            if (binder != null && child.path != null)
            {
                binder.bind(child.path);

                return binder.getHeight();
            }
        }
        catch (Exception e)
        {}

        return 0;
    }

    public void close()
    {
        this.color.picker.removeFromParent();
    }

    public void setLocation(FilteredResourceLocation location)
    {
        this.location = location;

        this.color.picker.setColor(location.color);
        this.scale.setValue(location.scale);
        this.scaleToLargest.toggled(location.scaleToLargest);
        this.shiftX.setValue(location.shiftX);
        this.shiftY.setValue(location.shiftY);

        this.pixelate.setValue(location.pixelate);
        this.erase.toggled(location.erase);

        this.autoSize.toggled(location.autoSize);
        this.sizeW.setValue(location.sizeW);
        this.sizeH.setValue(location.sizeH);
    }

    @Override
    protected void startDragging(GuiContext context)
    {
        super.startDragging(context);

        if (this.mouse == 0)
        {
            this.lastT = this.location.shiftX;
            this.lastV = this.location.shiftY;
        }
    }

    @Override
    protected void dragging(GuiContext context)
    {
        super.dragging(context);

        if (this.dragging && this.mouse == 0)
        {
            double dx = (context.mouseX - this.lastX) / this.scaleX.getZoom();
            double dy = (context.mouseY - this.lastY) / this.scaleY.getZoom();

            if (GuiUtils.isShiftKeyDown()) dx = 0;
            if (GuiUtils.isCtrlKeyDown()) dy = 0;

            this.location.shiftX = (int) (dx) + (int) this.lastT;
            this.location.shiftY = (int) (dy) + (int) this.lastV;

            this.shiftX.setValue(this.location.shiftX);
            this.shiftY.setValue(this.location.shiftY);
        }
    }

    @Override
    protected boolean shouldDrawCanvas(GuiContext context)
    {
        return this.picker.multiRL != null;
    }

    @Override
    protected void drawCanvasFrame(GuiContext context)
    {
        GuiTexturePicker.TextureBinder binder = GuiTexturePicker.textureBinder;

        if (binder == null)
        {
            return;
        }

        for (FilteredResourceLocation child : this.picker.multiRL.children)
        {
            try
            {
                binder.bind(child.path);
            }
            catch (Exception e)
            {
                continue;
            }

            int ow = binder.getWidth();
            int oh = binder.getHeight();
            int ww = ow;
            int hh = oh;

            if (child.scaleToLargest)
            {
                ww = this.w;
                hh = this.h;
            }
            else if (child.scale != 1)
            {
                ww = (int) (ww * child.scale);
                hh = (int) (hh * child.scale);
            }

            if (ww > 0 && hh > 0)
            {
                Area area = this.calculate(-this.w / 2 + child.shiftX, -this.h / 2 + child.shiftY, -this.w / 2 + child.shiftX + ww, -this.h / 2 + child.shiftY + hh);

                if (child == this.picker.currentFRL)
                {
                    GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x44ff0000);
                }

                /* Legacy applied the pixelate/erase preview shader here —
                 * deferred to S7's CPU-composited preview */
                GuiDraw.bindColor(child.color);

                try
                {
                    binder.bind(child.path);
                }
                catch (Exception e)
                {
                    GuiDraw.resetColor();

                    continue;
                }

                GuiDraw.drawBillboard(area.x, area.y, 0, 0, area.w, area.h, area.w, area.h);
                GuiDraw.resetColor();
            }
        }
    }
}
