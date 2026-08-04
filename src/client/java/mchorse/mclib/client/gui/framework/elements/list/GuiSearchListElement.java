package mchorse.mclib.client.gui.framework.elements.list;

import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiSearchListElement} (roadmap P35) — a search
 * text field composed above a list, with a gray ghost label while empty.
 */
public abstract class GuiSearchListElement<T> extends GuiElement
{
    public GuiTextElement search;
    public GuiListElement<T> list;
    public IKey label = IKey.EMPTY;

    public GuiSearchListElement(MinecraftClient mc, Consumer<List<T>> callback)
    {
        super(mc);

        this.search = new GuiTextElement(mc, 100, (str) -> this.filter(str, false));
        this.search.flex().relative(this).set(0, 0, 0, 20).w(1, 0);

        this.list = this.createList(mc, callback);
        this.list.flex().relative(this).set(0, 20, 0, 0).w(1, 0).h(1, -20);

        this.add(this.search, this.list);
    }

    public GuiSearchListElement<T> label(IKey label)
    {
        this.label = label;

        return this;
    }

    protected abstract GuiListElement<T> createList(MinecraftClient mc, Consumer<List<T>> callback);

    public void filter(String str, boolean fill)
    {
        if (fill)
        {
            this.search.setText(str);
        }

        this.list.filter(str);
    }

    @Override
    public void draw(GuiContext context)
    {
        super.draw(context);

        if (!this.search.field.isFocused() && this.search.field.getText().isEmpty())
        {
            GuiDraw.drawStringWithShadow(this.font, this.label.get(), this.search.area.x + 5, this.search.area.y + 6, 0x888888);
        }
    }
}
