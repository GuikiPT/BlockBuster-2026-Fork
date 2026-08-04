package mchorse.aperture.client.gui.config;

import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P186 — base class of a camera editor config section.
 *
 * <p>Every section is a titled vertical column added into {@link GuiCameraConfig}'s
 * scroll. Third parties (in-tree: Blockbuster's {@code GuiDirectorConfigOptions},
 * roadmap P185.1) contribute their own section through the
 * {@code CameraEditorEvent.Options} event.</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/config/GuiAbstractConfigOptions.java</p>
 */
public abstract class GuiAbstractConfigOptions extends GuiElement
{
    public GuiCameraEditor editor;

    public GuiAbstractConfigOptions(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc);

        this.editor = editor;

        this.add(Elements.label(this.getTitle()).background());
        this.flex().column(5).vertical().stretch().height(20).padding(10);
    }

    public abstract void update();

    public abstract IKey getTitle();
}
