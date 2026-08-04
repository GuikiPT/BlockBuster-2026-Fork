package mchorse.aperture.client.gui.utils;

import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTextElement;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.Icons;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * Text field with a small help icon in the top-right that opens a wiki link
 * (P185) — used by the entity-selector / math-expression modifier panels.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/utils/GuiTextHelpElement.java
 */
public class GuiTextHelpElement extends GuiTextElement
{
    public GuiIconElement help;
    public String link = "";

    public GuiTextHelpElement(MinecraftClient mc, int maxLength, Consumer<String> callback)
    {
        super(mc, maxLength, callback);

        this.setup(mc);
    }

    public GuiTextHelpElement(MinecraftClient mc, Consumer<String> callback)
    {
        super(mc, callback);

        this.setup(mc);
    }

    protected void setup(MinecraftClient mc)
    {
        this.help = new GuiIconElement(mc, Icons.HELP, (b) -> GuiUtils.openWebLink(this.link));
        this.help.flex().relative(this).x(1F, -1).y(1).wh(18, 18).anchorX(1F);
        this.help.hoverColor(0xff999999).iconColor(0xffcccccc);
        this.add(this.help);
    }

    public GuiTextHelpElement link(String link)
    {
        this.link = link;

        return this;
    }
}
