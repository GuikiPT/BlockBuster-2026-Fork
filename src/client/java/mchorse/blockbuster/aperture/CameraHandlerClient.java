package mchorse.blockbuster.aperture;

import mchorse.aperture.ClientProxy;
import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.events.CameraEditorEvent;
import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.gui.GuiDirectorConfigOptions;
import mchorse.blockbuster.aperture.gui.GuiPlayback;
import mchorse.blockbuster.aperture.network.common.PacketRequestLength;
import mchorse.blockbuster.audio.AudioRenderer;
import mchorse.blockbuster.client.gui.dashboard.GuiBlockbusterPanels;
import mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordingEditorPanel;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.scene.PacketSceneRequestCast;
import mchorse.blockbuster.network.common.scene.sync.PacketSceneGoto;
import mchorse.blockbuster.network.common.scene.sync.PacketScenePlay;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.recording.scene.SceneLocation;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.blockbuster.utils.mclib.BBIcons;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiIconElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDrawable;
import mchorse.mclib.client.gui.mclib.GuiDashboard;
import mchorse.mclib.client.gui.utils.Area;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.ScrollArea;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * P185.1 — the client half of {@link CameraHandler}: everything that fuses
 * Aperture's camera editor with Blockbuster's scenes and recording editor.
 *
 * <p>In 1.12.2 this lived inside {@code CameraHandler} itself as
 * {@code @SideOnly(CLIENT) @Method(modid = "aperture")} members plus the inner
 * {@code CameraGUIHandler}. The port splits it out because {@code CameraHandler}
 * sits in the <b>main</b> source set (both sides read it) while all of this
 * touches client-only GUI state; {@link #register()} installs the seams the
 * common class exposes and subscribes the five camera-editor callbacks.</p>
 *
 * <p>What it does, in legacy terms:</p>
 * <ul>
 *   <li><b>Scrub</b> → {@code PacketSceneGoto} for the synced scene, and the
 *       recording timeline follows the camera scrub;</li>
 *   <li><b>Play/pause</b> → {@code PacketScenePlay} PLAY/PAUSE;</li>
 *   <li><b>Rewind</b> → {@code PacketScenePlay} RESTART <i>plus</i> a
 *       client-side kill of every actor playing one of the scene's records;</li>
 *   <li><b>Options</b> → contributes {@link GuiDirectorConfigOptions} to the
 *       editor's config popup;</li>
 *   <li><b>Init</b> → builds the embedded recording-editor overlay
 *       ({@link #editorElement}) and re-lays the editor's own timeline row into
 *       {@link #cameraEditorElements};</li>
 *   <li><b>Screen open/close</b> → the enter/exit packet handshake and the
 *       recording panel hand-off.</li>
 * </ul>
 *
 * <p>Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster/aperture/CameraHandler.java
 * (the client members + {@code CameraGUIHandler}).</p>
 */
public class CameraHandlerClient
{
    /**
     * Camera editor integrations — the row that replaces the editor's own
     * timeline row, holding the audio waveform drawable, the editor timeline,
     * the two toggle buttons and {@link #editorElement}.
     */
    public static GuiElement cameraEditorElements;

    /**
     * The collapsible recording-editor overlay inside the camera editor
     * (timeline + action editor). Hidden by default; Ctrl+E toggles it.
     */
    public static GuiElement editorElement;

    private static boolean registered;

    /**
     * Install the {@link CameraHandler} client seams and subscribe the
     * camera-editor callbacks (legacy {@code registerClient} →
     * {@code registerHandlers}). Idempotent.
     */
    public static synchronized void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        CameraHandler.playerPositionUpdater = () ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.player != null && ClientProxy.cameraEditor != null)
            {
                ClientProxy.cameraEditor.position.set(mc.player);
            }
        };

        CameraHandler.recordPanelMover = (panel) ->
        {
            if (panel instanceof GuiRecordingEditorPanel)
            {
                moveRecordPanelToEditor((GuiRecordingEditorPanel) panel);
            }
        };

        CameraHandler.playbackScreenOpener = (location, scenes) ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc == null)
            {
                return;
            }

            GuiPlayback playback = new GuiPlayback();

            playback.setLocation(location, scenes);
            mc.setScreen(playback);
        };

        CameraHandler.cameraEditorOpener = () -> ClientProxy.openCameraEditor();

        ClientProxy.EVENT_BUS.onScrubbed(CameraHandlerClient::onCameraScrub);
        ClientProxy.EVENT_BUS.onPlayback(CameraHandlerClient::onCameraPlause);
        ClientProxy.EVENT_BUS.onRewind(CameraHandlerClient::onCameraRewind);
        ClientProxy.EVENT_BUS.onOptions(CameraHandlerClient::onCameraOptions);
        ClientProxy.EVENT_BUS.onInit(CameraHandlerClient::onCameraEditorInit);
    }

    /* Event listeners */

    /**
     * Legacy {@code onCameraScrub}: push the camera scrub onto the scene, and
     * drag the recording editor's timeline cursor along with it. Note the
     * scroll target is measured from {@code record.preDelay}, so the recording
     * timeline stays aligned with the scene clock rather than the record's own.
     */
    public static void onCameraScrub(CameraEditorEvent.Scrubbed event)
    {
        SceneLocation location = CameraHandler.get();

        if (location != null)
        {
            Dispatcher.sendToServer(new PacketSceneGoto(location, event.position, CameraHandler.actions.get()));
        }

        GuiBlockbusterPanels dashboard = BlockbusterClient.panels;

        if (dashboard != null && dashboard.recordingEditorPanel != null && dashboard.recordingEditorPanel.timeline.isVisible())
        {
            ScrollArea scroll = dashboard.recordingEditorPanel.timeline.scroll;

            scroll.scrollIntoView(scroll.scrollItemSize * (event.position - dashboard.recordingEditorPanel.record.preDelay), 2);
            dashboard.recordingEditorPanel.timeline.cursor = event.position;
        }
    }

    /** Legacy {@code onCameraPlause}. */
    public static void onCameraPlause(CameraEditorEvent.Playback event)
    {
        SceneLocation location = CameraHandler.get();

        if (location != null)
        {
            Dispatcher.sendToServer(new PacketScenePlay(location, event.play ? PacketScenePlay.PLAY : PacketScenePlay.PAUSE, event.position));
        }
    }

    /**
     * Legacy {@code onCameraRewind}: restart the scene server-side, then
     * <b>client-side</b> discard every actor that is playing one of the open
     * scene's records.
     *
     * <p>The kill is a deliberate desync hack — the server respawns the cast on
     * RESTART, and without dropping the local copies the player briefly sees
     * two of every actor. Do not "fix" it into a server round trip.</p>
     */
    public static void onCameraRewind(CameraEditorEvent.Rewind event)
    {
        SceneLocation location = CameraHandler.get();

        if (location == null)
        {
            return;
        }

        Dispatcher.sendToServer(new PacketScenePlay(location, PacketScenePlay.RESTART, event.position));

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null || mc.world == null || BlockbusterClient.panels == null || BlockbusterClient.panels.scenePanel == null)
        {
            return;
        }

        List<Replay> sceneReplays = BlockbusterClient.panels.scenePanel.getReplays();

        if (sceneReplays == null)
        {
            /* Port guard: legacy dereferenced this unconditionally and threw
             * when a playback button pointed at a scene the panel had not
             * opened (getReplays() is null for an empty location) */
            return;
        }

        List<String> replays = new ArrayList<String>();

        for (Replay replay : sceneReplays)
        {
            replays.add(replay.id);
        }

        List<EntityActor> doomed = new ArrayList<EntityActor>();

        for (Entity entity : mc.world.getEntities())
        {
            if (!(entity instanceof EntityActor actor) || !actor.isAlive())
            {
                continue;
            }

            RecordPlayer player = EntityUtils.getRecordPlayer(actor);

            if (player != null && player.record != null && replays.contains(player.record.filename))
            {
                doomed.add(actor);
            }
        }

        for (EntityActor actor : doomed)
        {
            /* Legacy setDead() — yarn's client-side removal */
            actor.discard();
        }
    }

    /** Legacy {@code onCameraOptions}. */
    public static void onCameraOptions(CameraEditorEvent.Options event)
    {
        event.options.add(new GuiDirectorConfigOptions(MinecraftClient.getInstance(), event.editor));
    }

    /**
     * Legacy {@code onCameraEditorInit}: build the embedded recording editor.
     *
     * <p>Two structural moves happen here and only here: the editor's own
     * timeline is <b>pulled out of</b> {@code editor.top} and re-added inside
     * {@link #cameraEditorElements} (which is why the two toggle buttons can sit
     * flush against it), and {@link #editorElement} is created to host the
     * recording timeline / action editor that {@link #moveRecordPanelToEditor}
     * later fills.</p>
     *
     * <p>The overlay's {@code draw} does two extra jobs: it dims the area behind
     * an open action-editor sub-panel, and while the camera runner is playing it
     * force-follows {@code runner.ticks} (not the timeline value) so the
     * recording cursor tracks playback.</p>
     */
    public static void onCameraEditorInit(CameraEditorEvent.Init event)
    {
        CameraHandler.location = null;

        GuiDashboard.get();

        MinecraftClient mc = MinecraftClient.getInstance();
        GuiCameraEditor editor = event.editor;
        GuiBlockbusterPanels panels = BlockbusterClient.panels;
        GuiRecordingEditorPanel record = panels == null ? null : panels.recordingEditorPanel;

        /* Just in case */
        if (record == null)
        {
            return;
        }

        editorElement = new GuiElement(mc)
        {
            @Override
            public boolean mouseClicked(GuiContext context)
            {
                return super.mouseClicked(context) || (record.actionEditor.delegate != null && record.actionEditor.area.isInside(context));
            }

            @Override
            public boolean mouseScrolled(GuiContext context)
            {
                return super.mouseScrolled(context) || (record.actionEditor.delegate != null && record.actionEditor.area.isInside(context));
            }

            @Override
            public void draw(GuiContext context)
            {
                if (this.isVisible() && record.actionEditor.delegate != null)
                {
                    Area area = record.actionEditor.delegate.area;

                    area.draw(0x66000000);
                }

                if (editor.getRunner().isRunning())
                {
                    ScrollArea scroll = record.timeline.scroll;

                    scroll.scrollIntoView(scroll.scrollItemSize * (int) (editor.getRunner().ticks - record.record.preDelay), 2);
                    record.timeline.cursor = (int) editor.getRunner().ticks;
                }

                super.draw(context);
            }
        };
        editorElement.noCulling();

        Consumer<GuiIconElement> refresh = (b) ->
        {
            boolean show = editorElement.isVisible();

            editor.panel.flex().h(1, show ? -150 : -70);
            editor.timeline.flex().y(1, show ? -100 : -20);
            record.records.flex().h(1, show ? -80 : 0);
            b.both(show ? Icons.DOWNLOAD : Icons.UPLOAD);

            editor.root.resize();
        };

        GuiIconElement open = new GuiIconElement(mc, BBIcons.EDITOR, (b) -> record.records.toggleVisible());
        GuiIconElement toggle = new GuiIconElement(mc, Icons.UPLOAD, (b) ->
        {
            if (!record.timeline.isVisible())
            {
                return;
            }

            editorElement.setVisible(!editorElement.isVisible());
            refresh.accept(b);
        });

        GuiDrawable drawable = new GuiDrawable((context) ->
        {
            int w = (int) (editor.root.area.w * Blockbuster.audioWaveformWidth.get());

            AudioRenderer.renderAll(editor.root.area.x + (editor.root.area.w - w) / 2, editor.timeline.area.y - 15, w, Blockbuster.audioWaveformHeight.get(), context.screen.width, context.screen.height);
            record.timeline.cursor = editor.timeline.value;
        });

        IKey category = IKey.lang("blockbuster.gui.aperture.keys.category");
        IKey toggleEditor = IKey.lang("blockbuster.gui.aperture.keys.toggle_editor");
        IKey detachScene = IKey.lang("blockbuster.gui.aperture.keys.detach_scene");
        IKey reloadScene = IKey.lang("blockbuster.gui.aperture.keys.reload_scene");

        GuiDirectorConfigOptions directorOptions = editor.config.getChildren(GuiDirectorConfigOptions.class).get(0);

        open.tooltip(IKey.lang("blockbuster.gui.dashboard.player_recording"), Direction.TOP);
        open.keys().register(IKey.lang("blockbuster.gui.aperture.keys.toggle_list"), LegacyKeyCodes.KEY_L, () -> open.clickItself(editor.context)).held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        toggle.tooltip(toggleEditor, Direction.TOP);
        toggle.keys().register(toggleEditor, LegacyKeyCodes.KEY_E, () -> toggle.clickItself(editor.context)).held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        toggle.keys().register(detachScene, LegacyKeyCodes.KEY_D, () -> directorOptions.detachScene.clickItself(editor.context)).held(LegacyKeyCodes.KEY_LSHIFT).category(category).active(() -> !editor.flight.isFlightEnabled() && directorOptions.detachScene.isEnabled());
        toggle.keys().register(reloadScene, LegacyKeyCodes.KEY_R, () -> directorOptions.reloadScene.clickItself(editor.context)).held(LegacyKeyCodes.KEY_LSHIFT).category(category).active(() -> !editor.flight.isFlightEnabled());

        editorElement.setVisible(false);

        toggle.flex().relative(editor.timeline).set(0, 0, 20, 20).x(1F);
        open.flex().relative(editor.timeline).set(-20, 0, 20, 20);
        editor.timeline.flex().x(30).w(1, -60);

        editor.top.remove(editor.timeline);
        cameraEditorElements = new GuiElement(mc).noCulling();
        cameraEditorElements.add(drawable, editor.timeline, toggle, open, editorElement);

        editor.top.add(cameraEditorElements);
        refresh.accept(toggle);
    }

    /**
     * Legacy {@code moveRecordPanelToEditor}: pull the recording editor's three
     * widgets out of the dashboard tree and re-flex them against the camera
     * editor. The record list is prepended into {@link #cameraEditorElements}
     * (so it draws under the toolbar row) while the timeline and action editor
     * go into the collapsible {@link #editorElement}; the list's height depends
     * on whether that overlay is currently open.
     */
    public static void moveRecordPanelToEditor(GuiRecordingEditorPanel panel)
    {
        if (editorElement == null || cameraEditorElements == null)
        {
            return;
        }

        GuiCameraEditor editor = ClientProxy.getCameraEditor();

        panel.timeline.removeFromParent();
        panel.timeline.flex().relative(editor.viewport);
        panel.actionEditor.removeFromParent();
        panel.actionEditor.flex().relative(editor.viewport);
        panel.records.removeFromParent();
        panel.records.flex().relative(editor.viewport).h(1F, editorElement.isVisible() ? -80 : 0);

        cameraEditorElements.prepend(panel.records);
        editorElement.add(panel.timeline, panel.actionEditor);
    }

    /**
     * Legacy {@code CameraGUIHandler.onGuiOpen} — driven by the
     * {@code MinecraftClient.setScreen} HEAD mixin (the port's
     * {@code GuiOpenEvent}), so {@code toOpen} is the incoming screen while
     * {@code mc.currentScreen} is still the outgoing one.
     *
     * <p>Entering the camera editor stashes the scrub into
     * {@link CameraHandler#tick} (which {@code ClientHandlerSceneLength} reads
     * back), optionally restarts the scene, and asks for the scene length and
     * cast. Leaving it saves the edited action and, when the runner is idle and
     * the config says so, stops the scene.</p>
     */
    public static void onScreenChange(Screen toOpen)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        /* The mixin that drives this fires for every screen change from the
         * very first frame; legacy's handler did not exist until
         * registerClient() ran, so mirror that. */
        if (!registered || mc == null || mc.player == null)
        {
            return;
        }

        Screen current = mc.currentScreen;
        SceneLocation location = CameraHandler.get();
        boolean toOpenCamera = toOpen instanceof GuiCameraEditor;

        if (location != null)
        {
            int tick = ClientProxy.getCameraEditor().timeline.value;

            if (!(current instanceof GuiCameraEditor) && toOpenCamera)
            {
                /* Camera editor opens */
                CameraHandler.tick = tick;

                if (CameraHandler.reload.get())
                {
                    Dispatcher.sendToServer(new PacketScenePlay(location, PacketScenePlay.START, tick));
                }

                Dispatcher.sendToServer(new PacketRequestLength(location));
                Dispatcher.sendToServer(new PacketSceneRequestCast(location));
            }
        }

        if (toOpenCamera)
        {
            GuiDashboard.get();

            GuiRecordingEditorPanel panel = BlockbusterClient.panels == null ? null : BlockbusterClient.panels.recordingEditorPanel;

            if (panel == null)
            {
                return;
            }

            panel.open();
            panel.appear();

            moveRecordPanelToEditor(panel);

            panel.records.setVisible(false);
        }
        else if (location != null && current instanceof GuiCameraEditor)
        {
            GuiRecordingEditorPanel panel = BlockbusterClient.panels == null ? null : BlockbusterClient.panels.recordingEditorPanel;

            if (panel != null)
            {
                panel.saveAction();
            }

            if (!((GuiCameraEditor) current).getRunner().isRunning() && CameraHandler.stopScene.get())
            {
                Dispatcher.sendToServer(new PacketScenePlay(location, PacketScenePlay.STOP, 0));
            }
        }
    }
}
