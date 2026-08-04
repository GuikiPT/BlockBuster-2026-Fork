package mchorse.blockbuster.aperture.gui;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.client.gui.config.GuiAbstractConfigOptions;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.aperture.network.common.PacketAudioShift;
import mchorse.blockbuster.client.gui.dashboard.panels.scene.GuiScenePanel;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.sync.PacketScenePlay;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.utils.Elements;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;

/**
 * P185.1 — Blockbuster's section of the camera editor's config popup.
 *
 * <p>Contributed through {@code CameraEditorEvent.Options} from the
 * {@code CameraHandlerClient} listener, so it only exists while a camera editor
 * does. The five controls are the scene↔camera coupling knobs: detach the
 * attached scene (stops it server-side and clears
 * {@link CameraHandler#location}), the three {@code aperture} config toggles
 * (reload / actions / stop_scene), a scene restart at the editor's current
 * scrub, and the scene's audio shift.</p>
 *
 * <p>Quirks kept 1:1:</p>
 * <ul>
 *   <li>{@code detachScene} reads the <b>static</b> {@link CameraHandler#location}
 *       (the attached scene), while {@code reloadScene} / {@code audioShift} read
 *       {@link CameraHandler#get()} — so a held playback button retargets those
 *       two but never the detach button;</li>
 *   <li>the detach button disables itself on click <b>and</b> is re-enabled from
 *       {@link #resize()}, which is the only place its enabled state tracks the
 *       location;</li>
 *   <li>{@link #update()} refreshes only the three toggles — the audio shift
 *       trackpad is written by the server's {@code PacketSceneLength} reply
 *       ({@code ClientHandlerSceneLength}), never re-read here;</li>
 *   <li>the audio-shift callback also writes the shift straight into the open
 *       scene panel's scene, but only when the panel's location equals the
 *       synced one.</li>
 * </ul>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/gui/GuiDirectorConfigOptions.java</p>
 */
public class GuiDirectorConfigOptions extends GuiAbstractConfigOptions
{
    private static GuiDirectorConfigOptions instance;

    public GuiButtonElement detachScene;
    public GuiToggleElement actions;
    public GuiToggleElement reload;
    public GuiToggleElement stopScene;
    public GuiButtonElement reloadScene;
    public GuiTrackpadElement audioShift;

    /**
     * The most recently constructed instance — legacy's access path from
     * {@code ClientHandlerSceneLength} (which writes {@link #audioShift}) and
     * from the camera editor keybinds. {@code null} before any camera editor
     * has been built.
     */
    public static GuiDirectorConfigOptions getInstance()
    {
        return instance;
    }

    public GuiDirectorConfigOptions(MinecraftClient mc, GuiCameraEditor editor)
    {
        super(mc, editor);

        this.detachScene = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.aperture.config.detach"), (b) ->
        {
            if (CameraHandler.location != null)
            {
                Dispatcher.sendToServer(new PacketScenePlay(CameraHandler.location, PacketScenePlay.STOP, 0));

                CameraHandler.location = null;
                b.setEnabled(false);
            }
        });

        this.reload = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.aperture.config.reload"), CameraHandler.reload.get(), (b) ->
        {
            CameraHandler.reload.set(this.reload.isToggled());
        });

        this.actions = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.aperture.config.actions"), CameraHandler.actions.get(), (b) ->
        {
            CameraHandler.actions.set(this.actions.isToggled());
        });

        this.stopScene = new GuiToggleElement(mc, IKey.lang("blockbuster.gui.aperture.config.stop_scene"), CameraHandler.stopScene.get(), (b) ->
        {
            CameraHandler.stopScene.set(this.stopScene.isToggled());
        });

        this.reloadScene = new GuiButtonElement(mc, IKey.lang("blockbuster.gui.aperture.config.reload_scene"), (b) ->
        {
            SceneLocation location = CameraHandler.get();

            if (location != null)
            {
                Dispatcher.sendToServer(new PacketScenePlay(location, PacketScenePlay.RESTART, ClientProxy.getCameraEditor().timeline.value));
            }
        });

        this.audioShift = new GuiTrackpadElement(mc, (value) ->
        {
            SceneLocation location = CameraHandler.get();

            if (location != null)
            {
                Dispatcher.sendToServer(new PacketAudioShift(location, value.intValue()));

                GuiScenePanel panel = BlockbusterClient.panels == null ? null : BlockbusterClient.panels.scenePanel;

                if (panel != null && panel.getLocation().equals(location))
                {
                    panel.getLocation().getScene().setAudioShift(value.intValue());
                }
            }
        });
        this.audioShift.integer().tooltip(IKey.lang("blockbuster.gui.director.audio_shift_tooltip"));

        this.add(this.detachScene, this.reload, this.actions, this.stopScene, this.reloadScene);
        this.add(Elements.label(IKey.lang("blockbuster.gui.director.audio_shift")).background(), this.audioShift);

        instance = this;
    }

    @Override
    public IKey getTitle()
    {
        return IKey.lang("blockbuster.gui.aperture.config.title");
    }

    @Override
    public void update()
    {
        this.reload.toggled(CameraHandler.reload.get());
        this.actions.toggled(CameraHandler.actions.get());
        this.stopScene.toggled(CameraHandler.stopScene.get());
    }

    @Override
    public void resize()
    {
        super.resize();

        this.detachScene.setEnabled(CameraHandler.location != null);
    }
}
