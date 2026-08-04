package mchorse.blockbuster.client.gui;

/**
 * Client recording-overlay state (roadmap P116) — the display state that
 * {@code PacketCaption} and {@code PacketPlayerRecording} push and the S10 P124
 * HUD ({@code GuiRecordingOverlay}) renders.
 *
 * <p>Two caption modes mirror legacy: {@code setCaption(text, false)} is plain
 * text (countdown, arbitrary captions); {@code setCaption(filename, true)} is
 * filename mode, which the HUD wraps in the {@code blockbuster.recording} l10n
 * with a live tick counter. This class only holds the state; the rendering /
 * l10n wrapping is P124.</p>
 */
public class RecordingOverlayState
{
    private boolean visible;
    private String caption = "";
    private boolean filename;

    public boolean isVisible()
    {
        return this.visible;
    }

    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }

    public String getCaption()
    {
        return this.caption;
    }

    /**
     * Whether the current caption is a record filename (filename mode) rather
     * than plain text.
     */
    public boolean isFilename()
    {
        return this.filename;
    }

    public void setCaption(String caption, boolean filename)
    {
        this.caption = caption == null ? "" : caption;
        this.filename = filename;
    }
}
