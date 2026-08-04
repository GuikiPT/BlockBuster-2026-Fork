package mchorse.mclib.client.gui.framework.elements.list;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;

/**
 * Port of McLib 2.4.3's {@code GuiStringSearchListElement} (roadmap P35).
 */
public class GuiStringSearchListElement extends GuiSearchListElement<String>
{
    public GuiStringSearchListElement(MinecraftClient mc, Consumer<List<String>> callback)
    {
        super(mc, callback);
    }

    @Override
    protected GuiListElement<String> createList(MinecraftClient mc, Consumer<List<String>> callback)
    {
        return new GuiStringListElement(mc, callback);
    }
}
