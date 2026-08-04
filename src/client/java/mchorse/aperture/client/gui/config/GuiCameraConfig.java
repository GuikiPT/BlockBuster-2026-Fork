package mchorse.aperture.client.gui.config;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.events.CameraEditorEvent;
import mchorse.mclib.client.gui.framework.elements.GuiScrollElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import net.minecraft.client.MinecraftClient;

/**
 * P186 — the camera editor's config popup (the gear button).
 *
 * <p>A scrolling column of {@link GuiAbstractConfigOptions} sections. The list
 * is assembled <b>once, in the constructor</b>: the editor's own
 * {@code cameraOptions} goes in first, then every section contributed by a
 * {@link CameraEditorEvent.Options} listener, in listener order. Blockbuster's
 * director options (P185.1) arrive that way.</p>
 *
 * <p>Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/client/gui/config/GuiCameraConfig.java</p>
 */
public class GuiCameraConfig extends GuiScrollElement
{
    public GuiCameraEditor editor;

    public GuiCameraConfig(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc);

        this.editor = editor;

        CameraEditorEvent.Options event = new CameraEditorEvent.Options(editor);

        event.options.add(editor.cameraOptions);

        ClientProxy.EVENT_BUS.post(event);

        for (GuiAbstractConfigOptions option : event.options)
        {
            this.add(option);
        }

        this.flex().column(0).vertical().stretch().scroll();
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xaa000000);

        super.draw(context);
    }
}
