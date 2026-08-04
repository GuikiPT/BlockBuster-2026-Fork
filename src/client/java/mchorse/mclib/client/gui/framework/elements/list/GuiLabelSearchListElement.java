package mchorse.mclib.client.gui.framework.elements.list;

import mchorse.mclib.client.gui.utils.Label;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port of McLib 2.4.3's {@code GuiLabelSearchListElement} (roadmap P35).
 */
public class GuiLabelSearchListElement<T> extends GuiSearchListElement<Label<T>>
{
    public GuiLabelSearchListElement(MinecraftClient mc, Consumer<List<Label<T>>> callback)
    {
        super(mc, callback);
    }

    @Override
    protected GuiListElement<Label<T>> createList(MinecraftClient mc, Consumer<List<Label<T>>> callback)
    {
        return new GuiLabelListElement<T>(mc, callback);
    }
}
