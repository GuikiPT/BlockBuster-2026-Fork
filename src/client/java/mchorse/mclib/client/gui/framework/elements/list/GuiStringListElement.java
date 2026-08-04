package mchorse.mclib.client.gui.framework.elements.list;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiStringListElement} (roadmap P35).
 */
public class GuiStringListElement extends GuiListElement<String>
{
    public GuiStringListElement(MinecraftClient mc, Consumer<List<String>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 16;
    }

    @Override
    protected boolean sortElements()
    {
        Collections.sort(this.list);

        return true;
    }

    @Override
    protected String elementToString(String element)
    {
        return element;
    }
}
