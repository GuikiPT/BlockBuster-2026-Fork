package mchorse.blockbuster.client.gui.dashboard.panels.scene;

import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.GuiBase;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.utils.ColorUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port of Blockbuster 2.7.2's {@code GuiReplaySelector} (roadmap S11 P133) —
 * the horizontal replay strip in the director/scene panel. It draws each
 * replay's morph (or a chicken placeholder when null), highlights the current
 * selection, and shows a floating tooltip with the hovered replay's id.
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/scene/GuiReplaySelector.java</p>
 */
public class GuiReplaySelector extends GuiListElement<Replay>
{
    private String hovered;
    private int hoverX;
    private int hoverY;

    public GuiReplaySelector(MinecraftClient mc, Consumer<List<Replay>> callback)
    {
        super(mc, callback);

        this.horizontal().sorting();
        this.scroll.scrollItemSize = 40;
    }

    @Override
    public void draw(GuiContext context)
    {
        this.hovered = null;

        super.draw(context);

        if (this.hovered != null)
        {
            int w = this.font.getWidth(this.hovered);
            int x = this.hoverX - w / 2;

            GuiDraw.drawRect(x - 2, this.hoverY - 1, x + w + 2, this.hoverY + 9, ColorUtils.HALF_BLACK);
            GuiDraw.drawStringWithShadow(this.font, this.hovered, x, this.hoverY, 0xffffff);
        }
        else if (this.getList().isEmpty())
        {
            GuiDraw.drawCenteredString(this.font, I18n.translate("blockbuster.gui.director.no_replays"), this.area.mx(), this.area.my() - 6, 0xffffff);
        }
    }

    @Override
    public void drawListElement(Replay replay, int i, int x, int y, boolean hover, boolean selected)
    {
        int w = this.scroll.scrollItemSize;
        int h = this.scroll.h;
        boolean isDragging = this.isDragging() && this.getDraggingIndex() == i;

        if (isDragging)
        {
            y -= 20;
        }
        else
        {
            x += w / 2;
        }

        if (selected && !isDragging)
        {
            GuiDraw.drawRect(x - w / 2, y, x + w / 2, y + h, 0xaa000000 + McLib.primaryColor.get());
            GuiDraw.scissor(x - w / 2, y, w, h, GuiBase.getCurrent());
        }

        if (replay.morph != null && this.mc != null && this.mc.player != null)
        {
            replay.morph.renderOnScreen(this.mc.player, x, y + (int) (this.scroll.h * 0.8F), 24, 1);
        }
        else
        {
            /* Legacy's GlStateManager.color(1, 1, 1) reset before the icon has
             * no analog — the icon draw sets its own colour in core profile. */
            BBIcons.CHICKEN.render(x - 8, y + this.scroll.h / 2 - 8);
        }

        if (selected && !isDragging)
        {
            GuiDraw.drawOutline(x - w / 2, y, x + w / 2, y + h, 0xff000000 + McLib.primaryColor.get(), 2);
            GuiDraw.unscissor(GuiBase.getCurrent());
        }

        if (hover && !replay.id.isEmpty() && this.hovered == null)
        {
            this.hovered = replay.id;
            this.hoverX = x;
            this.hoverY = y + this.scroll.h / 2;
        }
    }
}
