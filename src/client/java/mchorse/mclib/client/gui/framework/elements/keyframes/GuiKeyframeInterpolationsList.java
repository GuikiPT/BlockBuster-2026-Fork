package mchorse.mclib.client.gui.framework.elements.keyframes;

import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interpolations list
 *
 * (Port of McLib 2.4.3's {@code GuiKeyframeInterpolationsList}, roadmap P35;
 * the keyframe editors that consume it land in P43.)
 */
public class GuiKeyframeInterpolationsList extends GuiListElement<KeyframeInterpolation>
{
    public GuiKeyframeInterpolationsList(MinecraftClient mc, Consumer<List<KeyframeInterpolation>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 16;

        for (KeyframeInterpolation interp : KeyframeInterpolation.values())
        {
            this.add(interp);
        }

        this.sort();
        this.background();
    }

    @Override
    protected boolean sortElements()
    {
        Collections.sort(this.list, (o1, o2) -> o1.key.compareTo(o2.key));

        return true;
    }

    @Override
    protected String elementToString(KeyframeInterpolation element)
    {
        return I18n.translate(element.getKey());
    }
}
