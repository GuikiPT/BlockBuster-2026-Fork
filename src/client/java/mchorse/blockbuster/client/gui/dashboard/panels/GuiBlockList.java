package mchorse.blockbuster.client.gui.dashboard.panels;

import java.util.List;
import java.util.function.Consumer;

import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.ScrollArea;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * GUI block list (roadmap P136).
 *
 * <p>Verbatim port of 1.12.2
 * {@code client/gui/dashboard/panels/GuiBlockList.java}: the abstract base of
 * the model-block / texture-manager block pickers. It splits the widget into a
 * fixed 30-px title header band and a scroll area offset 30-px below it (item
 * size 20). Subclasses implement {@link #addBlock(BlockPos)} to resolve the
 * block entity at a position and, if it is of the expected type, push it into
 * the list.</p>
 *
 * <p>Port note: the McLib {@link GuiListElement} constructor aliases
 * {@code area == scroll} (one {@link ScrollArea}); this class re-separates them
 * (a distinct {@link Area} for the header + a {@link ScrollArea} for the list)
 * exactly like legacy so {@link #resize()} can offset the scroll region below
 * the title band.</p>
 */
public abstract class GuiBlockList<T> extends GuiListElement<T>
{
    /**
     * Title of this panel
     */
    public IKey title;

    public GuiBlockList(MinecraftClient mc, IKey title, Consumer<List<T>> callback)
    {
        super(mc, callback);

        this.title = title;
        this.area = new Area();
        this.scroll = new ScrollArea(20);
    }

    public abstract boolean addBlock(BlockPos pos);

    @Override
    public void resize()
    {
        super.resize();

        this.scroll.copy(this.area);
        this.scroll.y += 30;
        this.scroll.h -= 30;
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xff333333);

        GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.y + 30, 0x44000000);
        GuiDraw.drawStringWithShadow(this.font, this.title.get(), this.area.x + 10, this.area.y + 11, 0xcccccc);

        super.draw(context);
    }
}
