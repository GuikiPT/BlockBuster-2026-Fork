package mchorse.blockbuster.client.gui.dashboard.panels.model_editor.utils;

import com.mojang.blaze3d.systems.RenderSystem;

import mchorse.blockbuster.api.ModelLimb;
import mchorse.blockbuster.client.gui.dashboard.panels.model_editor.tabs.GuiModelLimbs;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiCanvasEditor;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * The limb UV canvas (roadmap P137) — a pannable/zoomable view of the model's
 * skin with the selected limb's box unwrap drawn over it, plus the two
 * trackpads that move {@code limb.texture}.
 *
 * <p>Legacy source (ported 1:1):
 * {@code blockbuster-1.12/.../model_editor/utils/GuiTextureCanvas.java}.</p>
 *
 * <p><b>1.20.4 mapping.</b> {@code mc.renderEngine.bindTexture(rl)} &rarr;
 * {@link RenderSystem#setShaderTexture(int, Identifier)} (what
 * {@link GuiDraw#drawBillboard} samples from); {@code Gui.drawRect} &rarr;
 * {@link GuiDraw#drawRect}. Everything else — the face colours, the two blacked
 * out corner holes and the red patch outline — is legacy verbatim.</p>
 *
 * <p><b>The unwrap layout</b> (legacy verbatim, box-limb UV convention with
 * {@code lw}/{@code lh}/{@code ld} = the limb's size and {@code lx}/{@code ly}
 * its texture offset):</p>
 * <ul>
 * <li>top {@code 0x5500ff00} and bottom {@code 0x5500ffff} along the first row,
 * each {@code lw} wide and {@code ld} tall, starting at {@code lx + ld};</li>
 * <li>front {@code 0x550000ff} and back {@code 0x55ff00ff} on the second row;</li>
 * <li>left {@code 0x55ff0000} and right {@code 0x55ffff00} flanking them;</li>
 * <li>the two unused {@code ld × ld} corners of the first row painted out with
 * {@code 0xdd000000};</li>
 * <li>a {@code 0xffff0000} outline around the whole
 * {@code (ld * 2 + lw * 2) × (ld + lh)} patch.</li>
 * </ul>
 *
 * <p><b>One added guard.</b> The unwrap draw returns early when no limb is
 * selected. Legacy dereferenced {@code panel.limb} unconditionally — reachable
 * only in the window between a model load failing and a limb being picked, but
 * the total-reader rule says a draw never throws.</p>
 */
public class GuiTextureCanvas extends GuiCanvasEditor
{
    public GuiTrackpadElement x;
    public GuiTrackpadElement y;
    public GuiIconElement close;

    public GuiModelLimbs panel;

    public GuiTextureCanvas(MinecraftClient mc, GuiModelLimbs panel)
    {
        super(mc);

        this.panel = panel;

        this.close = new GuiIconElement(mc, Icons.CLOSE, (b) -> this.toggleVisible());
        this.close.flex().relative(this).x(1F, -25).y(5);

        this.x = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.getPanel().limb.texture[0] = value.intValue();
            this.panel.getPanel().rebuildModel();
        });
        this.x.limit(0, 8192, true);

        this.y = new GuiTrackpadElement(mc, (value) ->
        {
            this.panel.getPanel().limb.texture[1] = value.intValue();
            this.panel.getPanel().rebuildModel();
        });
        this.y.limit(0, 8192, true);

        this.editor.add(Elements.label(IKey.lang("blockbuster.gui.me.limbs.texture")).background(), this.x, this.y);
        this.add(this.editor, this.close);

        this.markContainer();
    }

    @Override
    protected boolean shouldDrawCanvas(GuiContext context)
    {
        return this.panel.getPanel().modelRenderer.texture != null;
    }

    @Override
    protected void drawCanvasFrame(GuiContext context)
    {
        Identifier location = this.panel.getPanel().modelRenderer.texture;
        Area area = this.calculate(-this.w / 2, -this.h / 2, this.w / 2, this.h / 2);

        RenderSystem.setShaderTexture(0, location);
        GuiDraw.drawBillboard(area.x, area.y, 0, 0, area.w, area.h, area.w, area.h);

        ModelLimb limb = this.panel.getPanel().limb;

        if (limb == null)
        {
            return;
        }

        int lx = limb.texture[0];
        int ly = limb.texture[1];
        int lw = limb.size[0];
        int lh = limb.size[1];
        int ld = limb.size[2];

        /* Top and bottom */
        area = this.calculateRelative(lx + ld, ly, lx + ld + lw, ly + ld);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x5500ff00);

        area = this.calculateRelative(lx + ld + lw, ly, lx + ld + lw + lw, ly + ld);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x5500ffff);

        /* Front and back */
        area = this.calculateRelative(lx + ld, ly + ld, lx + ld + lw, ly + ld + lh);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x550000ff);

        area = this.calculateRelative(lx + ld * 2 + lw, ly + ld, lx + ld * 2 + lw * 2, ly + ld + lh);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x55ff00ff);

        /* Left and right */
        area = this.calculateRelative(lx, ly + ld, lx + ld, ly + ld + lh);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x55ff0000);

        area = this.calculateRelative(lx + ld + lw, ly + ld, lx + ld * 2 + lw, ly + ld + lh);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0x55ffff00);

        /* Holes */
        area = this.calculateRelative(lx, ly, lx + ld, ly + ld);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0xdd000000);

        area = this.calculateRelative(lx + ld + lw * 2, ly, lx + ld * 2 + lw * 2, ly + ld);

        GuiDraw.drawRect(area.x, area.y, area.ex(), area.ey(), 0xdd000000);

        /* Outline */
        area = this.calculateRelative(lx, ly, lx + ld * 2 + lw * 2, ly + ld + lh);

        GuiDraw.drawOutline(area.x, area.y, area.ex(), area.ey(), 0xffff0000);
    }
}
