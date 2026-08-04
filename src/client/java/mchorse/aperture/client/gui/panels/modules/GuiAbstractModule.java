package mchorse.aperture.client.gui.panels.modules;

import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import net.minecraft.client.MinecraftClient;

/**
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/panels/modules/GuiAbstractModule.java
 */
public abstract class GuiAbstractModule extends GuiElement
{
    protected GuiCameraEditor editor;

    public GuiAbstractModule(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc);

        this.editor = editor;
    }
}
