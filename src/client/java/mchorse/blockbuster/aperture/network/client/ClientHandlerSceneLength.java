package mchorse.blockbuster.aperture.network.client;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.aperture.gui.GuiDirectorConfigOptions;
import mchorse.blockbuster.aperture.network.common.PacketSceneLength;
import mchorse.mclib.network.ClientMessageHandler;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client receiver for {@link PacketSceneLength} (roadmap P185.1). 1:1 port of
 * 1.12.2 {@code aperture/network/client/ClientHandlerSceneLength.java}.
 *
 * <p>This is what makes the camera editor's scrub survive a reopen: the scrub
 * is restored from the {@link CameraHandler#tick} static (captured when the
 * editor screen last opened), not from the packet, while the packet supplies
 * the scene's max length (the scrub ceiling and timeline view bound) and its
 * audio shift.</p>
 *
 * <p>{@code GuiDirectorConfigOptions.getInstance()} is non-null here because
 * the reply can only arrive after an editor was built (the request is sent from
 * the editor-open hook), and building one constructs the director options
 * section — the same invariant legacy relied on.</p>
 */
public class ClientHandlerSceneLength extends ClientMessageHandler<PacketSceneLength>
{
    @Override
    public void run(ClientPlayerEntity player, PacketSceneLength message)
    {
        GuiCameraEditor editor = ClientProxy.getCameraEditor();

        editor.maxScrub = message.length;
        editor.timeline.value = CameraHandler.tick;
        editor.updateValues();
        editor.timeline.scale.view(editor.timeline.scale.getMinValue(), Math.max((int) editor.getProfile().getDuration(), message.length));

        GuiDirectorConfigOptions.getInstance().audioShift.setValue(message.shift);
    }
}
