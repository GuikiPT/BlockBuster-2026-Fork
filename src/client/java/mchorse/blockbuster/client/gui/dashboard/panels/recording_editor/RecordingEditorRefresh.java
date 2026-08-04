package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.recording.data.Record;

/**
 * S22 P236 — the "refresh the open recording editor" seam the three record
 * packet handlers go through.
 *
 * <p>1.12.2's {@code ClientHandlerActions}, {@code ClientHandlerFramesLoad} and
 * {@code ClientHandlerRequestedFrames} each poked
 * {@code ClientProxy.panels.recordingEditorPanel} directly (the legacy source
 * even carries a {@code //TODO This needs refactoring... ClientHandlerActions
 * receiver shouldn't control GUI...} on the panel method). The port landed all
 * three handlers with the call replaced by a {@code TODO(S12)} comment, so the
 * client record cache updated underneath an editor that kept showing stale
 * frames and actions — action edits pushed from the server never appeared, and
 * a re-requested record left the pre/post delay fields wrong.</p>
 *
 * <p>The indirection (rather than an inline {@code panels.recordingEditorPanel
 * != null} check, which is what {@code ClientHandlerActionList} does) exists so
 * the routing is assertable headlessly: {@link GuiRecordingEditorPanel} cannot
 * be constructed without a live {@code MinecraftClient}. Production installs
 * {@link PanelRefresher}, which is the legacy body verbatim.</p>
 */
public class RecordingEditorRefresh
{
    /**
     * How a received record reaches the open editor. {@code null} until
     * {@link #install()} runs (and on a dedicated server); both dispatch
     * helpers below treat that as "no editor to refresh".
     */
    public static IRecordingEditorRefresher handler;

    private RecordingEditorRefresh()
    {}

    /** Point the seam at the real dashboard panel. Idempotent. */
    public static void install()
    {
        if (!(handler instanceof PanelRefresher))
        {
            handler = new PanelRefresher();
        }
    }

    /**
     * {@code ClientHandlerActions}' half: the server answered a
     * {@code PacketRequestAction} that asked for the editor to be opened on the
     * record, so select it outright.
     */
    public static void selectRecord(Record record)
    {
        if (handler != null)
        {
            handler.selectRecord(record);
        }
    }

    /**
     * The frames half: a record the client already had was re-delivered, so
     * refresh it <b>only</b> if it is the one currently being edited — that
     * filename check lives inside {@link GuiRecordingEditorPanel#reselectRecord}
     * exactly as in 1.12.2, which is why both call sites are unconditional.
     */
    public static void reselectRecord(Record record)
    {
        if (handler != null)
        {
            handler.reselectRecord(record);
        }
    }

    /** The seam's contract. */
    public interface IRecordingEditorRefresher
    {
        void selectRecord(Record record);

        void reselectRecord(Record record);
    }

    /**
     * Production implementation — the legacy bodies, including their null
     * guards. The panel field is null whenever the dashboard has never been
     * opened (or was closed, which nulls it in
     * {@code GuiBlockbusterPanels.removePanels}).
     */
    public static class PanelRefresher implements IRecordingEditorRefresher
    {
        @Override
        public void selectRecord(Record record)
        {
            GuiRecordingEditorPanel panel = panel();

            if (panel != null)
            {
                panel.selectRecord(record);
            }
        }

        @Override
        public void reselectRecord(Record record)
        {
            GuiRecordingEditorPanel panel = panel();

            if (panel != null)
            {
                panel.reselectRecord(record);
            }
        }

        private static GuiRecordingEditorPanel panel()
        {
            return BlockbusterClient.panels == null ? null : BlockbusterClient.panels.recordingEditorPanel;
        }
    }
}
