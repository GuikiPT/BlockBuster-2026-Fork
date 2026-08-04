package mchorse.aperture.client.gui.utils;

import mchorse.aperture.camera.data.InterpolationType;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/utils/GuiInterpolationTypeList.java
 *
 * (Legacy {@code I18n.format} → {@code Text.translatable(...).getString()},
 * same as the IInterpolation port.)
 */
public class GuiInterpolationTypeList extends GuiListElement<InterpolationType>
{
    public GuiInterpolationTypeList(MinecraftClient mc, Consumer<List<InterpolationType>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 16;

        for (InterpolationType interp : InterpolationType.values())
        {
            this.add(interp);
        }

        this.background().cancelScrollEdge().sort();
    }

    @Override
    protected boolean sortElements()
    {
        Collections.sort(this.list, Comparator.comparing(o -> o.name));

        return true;
    }

    @Override
    protected String elementToString(InterpolationType element)
    {
        return Text.translatable(element.getKey()).getString();
    }
}
