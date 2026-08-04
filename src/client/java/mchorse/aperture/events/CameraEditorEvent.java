package mchorse.aperture.events;

import java.util.ArrayList;
import java.util.List;

import mchorse.aperture.client.gui.GuiCameraEditor;
import mchorse.aperture.client.gui.config.GuiAbstractConfigOptions;

/**
 * Base class for all camera editor events (P183).
 *
 * Port note: legacy extended Forge's {@code Event} and was posted on
 * {@code ClientProxy.EVENT_BUS} (a Forge {@code EventBus}); the payload
 * classes are ported verbatim, dispatch happens through the typed
 * {@link CameraEditorCallbacks} registry instead.
 *
 * Legacy source: .tools/legacy-src/aperture/src/main/java/mchorse/aperture/events/CameraEditorEvent.java
 */
public abstract class CameraEditorEvent
{
    public final GuiCameraEditor editor;

    public CameraEditorEvent(GuiCameraEditor editor)
    {
        this.editor = editor;
    }

    /**
     * Camera editor initiate event
     */
    public static class Init extends CameraEditorEvent
    {
        public Init(GuiCameraEditor editor)
        {
            super(editor);
        }
    }

    /**
     * Camera editor event when the playback timeline was scrubbed
     */
    public static class Scrubbed extends CameraEditorEvent
    {
        /**
         * Whether camera runner is running current camera profile
         */
        public boolean isRunning;

        /**
         * Position to which user scrubbed
         */
        public int position;

        public Scrubbed(GuiCameraEditor editor, boolean isRunning, int position)
        {
            super(editor);

            this.isRunning = isRunning;
            this.position = position;
        }
    }

    /**
     * Camera editor event for notifying playback of the camera
     */
    public static class Playback extends CameraEditorEvent
    {
        /**
         * Play is true and pause is false
         */
        public boolean play;

        /**
         * Position at which camera editor started playing/was paused
         */
        public int position;

        public Playback(GuiCameraEditor editor, boolean play, int position)
        {
            super(editor);

            this.play = play;
            this.position = position;
        }
    }

    /**
     * Camera editor event for notifying rewind of the camera, i.e. when camera playback was
     * finished and requires restore
     */
    public static class Rewind extends CameraEditorEvent
    {
        /**
         * Position at which camera editor started playing/was paused
         */
        public int position;

        public Rewind(GuiCameraEditor editor, int position)
        {
            super(editor);

            this.position = position;
        }
    }

    /**
     * Camera editor event for loading camera options.
     *
     * <p>Consumers add their own {@link GuiAbstractConfigOptions} section into
     * {@link #options}; {@link mchorse.aperture.client.gui.config.GuiCameraConfig}
     * seeds the list with the editor's own section and adds every contributed
     * one in order (in-tree consumer: Blockbuster's
     * {@code GuiDirectorConfigOptions}, roadmap P185.1).</p>
     */
    public static class Options extends CameraEditorEvent
    {
        public final List<GuiAbstractConfigOptions> options = new ArrayList<GuiAbstractConfigOptions>();

        public Options(GuiCameraEditor editor)
        {
            super(editor);
        }
    }
}
