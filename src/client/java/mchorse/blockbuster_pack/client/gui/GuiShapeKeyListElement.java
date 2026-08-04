package mchorse.blockbuster_pack.client.gui;

import java.util.List;
import java.util.function.Consumer;

import mchorse.blockbuster.api.formats.obj.ShapeKey;
import mchorse.mclib.client.gui.framework.elements.list.GuiListElement;
import net.minecraft.client.MinecraftClient;

/**
 * List element that renders {@link ShapeKey}s by their name (port of
 * Blockbuster 2.7.2, roadmap P144).
 *
 * <p>In legacy 1.12.2 this lived as {@code GuiCustomMorph.GuiShapeKeyListElement}.
 * It is landed here as a standalone class so P144 ({@link GuiShapeKeysEditor})
 * is self-contained; S14's {@code GuiCustomMorph} adopts this type rather than
 * re-declaring it. The 16px row size and name-only rendering are the observable
 * contract.</p>
 */
public class GuiShapeKeyListElement extends GuiListElement<ShapeKey>
{
    public GuiShapeKeyListElement(MinecraftClient mc, Consumer<List<ShapeKey>> callback)
    {
        super(mc, callback);

        this.scroll.scrollItemSize = 16;
    }

    @Override
    protected String elementToString(ShapeKey element)
    {
        return element.name;
    }
}
