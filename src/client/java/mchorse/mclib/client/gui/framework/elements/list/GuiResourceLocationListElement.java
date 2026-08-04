package mchorse.mclib.client.gui.framework.elements.list;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import mchorse.mclib.utils.resources.ResourceLocation;
import net.minecraft.client.MinecraftClient;

/**
 * Similar to {@link GuiStringListElement}, but uses {@link ResourceLocation}s.
 *
 * (Port of McLib 2.4.3's {@code GuiResourceLocationListElement}, roadmap P35.
 * Uses the bundled {@code mchorse.mclib.utils.resources.ResourceLocation} —
 * the type every legacy skin/texture format parses into.)
 */
public class GuiResourceLocationListElement extends GuiListElement<ResourceLocation>
{
    public GuiResourceLocationListElement(MinecraftClient mc, Consumer<List<ResourceLocation>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 16;
    }

    @Override
    protected boolean sortElements()
    {
        Collections.sort(this.list, (a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        return true;
    }
}
