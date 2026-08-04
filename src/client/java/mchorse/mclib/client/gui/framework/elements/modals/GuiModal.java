package mchorse.mclib.client.gui.framework.elements.modals;

import mchorse.mclib.client.gui.framework.elements.GuiDelegateElement;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.IGuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Supplier;

/**
 * Parent class for all modals
 *
 * Best to be used with {@link GuiDelegateElement}.
 *
 * (Port of McLib 2.4.3's {@code GuiModal}, roadmap P36. The
 * one-modal-per-parent refusal returns false silently — callers chain on
 * it.)
 */
public abstract class GuiModal extends GuiElement
{
    public IKey label;
    public int y;

    public GuiElement bar;

    public static boolean hasModal(GuiElement parent)
    {
        for (IGuiElement element : parent.getChildren())
        {
            if (element instanceof GuiModal)
            {
                return true;
            }
        }

        return false;
    }

    public static boolean addModal(GuiElement parent, Supplier<GuiModal> supplier)
    {
        if (hasModal(parent) || supplier == null)
        {
            return false;
        }

        GuiModal modal = supplier.get();

        modal.resize();
        parent.add(modal);

        return true;
    }

    public static boolean addFullModal(GuiElement parent, Supplier<GuiModal> supplier)
    {
        if (hasModal(parent) || supplier == null)
        {
            return false;
        }

        GuiModal modal = supplier.get();

        modal.flex().relative(parent).wh(1F, 1F);
        modal.resize();
        parent.add(modal);

        return true;
    }

    public GuiModal(MinecraftClient mc, IKey label)
    {
        super(mc);

        this.bar = new GuiElement(mc);
        this.bar.flex().relative(this).y(1F).w(1F).h(40).anchorY(1F).row(10).padding(10);
        this.add(this.bar);

        this.label = label;
        this.markContainer();
    }

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        return super.mouseClicked(context) || this.area.isInside(context);
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        return super.mouseScrolled(context) || this.area.isInside(context);
    }

    @Override
    public void draw(GuiContext context)
    {
        GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xcc000000);

        this.y = 0;
        int y = this.area.y + 10;

        List<String> lines = GuiDraw.listFormattedStringToWidth(this.label.get(), this.area.w - 20, (s) -> GuiDraw.textWidth(this.font, s));

        for (String line : lines)
        {
            GuiDraw.drawStringWithShadow(this.font, line, this.area.x + 10, y + this.y, 0xffffff);
            this.y += 11;
        }

        super.draw(context);
    }
}
