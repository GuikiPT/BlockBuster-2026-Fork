package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor;

import mchorse.blockbuster.BlockbusterClient;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringSearchListElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.keys.IKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Record list — the recording editor's server-record name listing (roadmap
 * P138).
 *
 * <p>1:1 port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster.client.gui.dashboard.panels.recording_editor.GuiRecordList}
 * (95 lines). The widget is a thin decorated frame around a
 * {@link GuiStringSearchListElement}: a 30-px darkened header strip carrying a
 * title, and the searchable list inset by 10 px.</p>
 *
 * <p>Two display modes, decided <b>per {@link #add(List)} call</b> (never
 * cached beyond it):</p>
 * <ul>
 *   <li><b>All records</b> ({@code director == false}, header
 *       {@code blockbuster.gui.record_editor.title}) — the default: every
 *       filename the server sent through {@code PacketActionList} is listed.</li>
 *   <li><b>Scene's records</b> ({@code director == true}, header
 *       {@code blockbuster.gui.record_editor.directors}) — only the ids of the
 *       currently edited scene's replays, and only those the server actually
 *       reported. Entered when the Aperture camera editor is open <i>and</i> a
 *       scene can be synced <i>and</i> the scene panel exposes replays.</li>
 * </ul>
 *
 * <p>Legacy quirks preserved verbatim:</p>
 * <ul>
 *   <li>The "all" branch appends through {@code list.add(record)} (which calls
 *       {@code update()} per element) while the director branch appends
 *       straight into {@code list.getList()} and relies on the trailing
 *       {@code update()} — the observable difference is nil but the call shape
 *       is kept for diff-ability.</li>
 *   <li>{@link #add(List)} does <b>not</b> clear first: the director branch
 *       explicitly de-duplicates against the existing list content, so
 *       repeated calls accumulate rather than replace. {@code open()} on the
 *       panel is what calls {@link #clear()} first.</li>
 *   <li>The director branch iterates the <i>replays</i> (scene order), not the
 *       server list, so the list is ordered by replay index.</li>
 *   <li>{@code filter("", true)} is re-applied after every mutation, which also
 *       wipes whatever the user typed into the search field.</li>
 *   <li>{@link #toggleVisible()} and {@link #setVisible(boolean)} both notify
 *       the panel so the action editor can reclaim the 120 px this list
 *       occupies.</li>
 * </ul>
 *
 * <p>Legacy source:
 * blockbuster-1.12/src/main/java/mchorse/blockbuster/client/gui/dashboard/panels/recording_editor/GuiRecordList.java</p>
 */
public class GuiRecordList extends GuiElement
{
    /**
     * Legacy {@code CameraHandler.canSync()} ({@code CameraHandler.get() != null}
     * — "the held playback item names a scene, or the camera editor stashed a
     * {@code SceneLocation}"), kept behind an overridable supplier so the
     * director branch stays headlessly testable (the house idiom used by the
     * rest of {@link CameraHandler}'s Aperture seams).
     */
    public static BooleanSupplier canSync = CameraHandler::canSync;

    public GuiRecordingEditorPanel panel;
    public GuiStringSearchListElement records;
    public boolean director;

    public GuiRecordList(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc);

        this.panel = panel;
        this.records = new GuiStringSearchListElement(mc, (str) -> this.panel.selectRecord(str.get(0)));
        this.records.flex().relative(this.area).set(10, 35, 0, 0).h(1, -45).w(1, -20);
        this.records.label = IKey.lang("blockbuster.gui.search");

        this.add(this.records);
    }

    public void clear()
    {
        this.records.list.clear();
        this.records.filter("", true);
    }

    public void add(List<String> records)
    {
        List<Replay> replays = BlockbusterClient.panels.scenePanel.getReplays();
        boolean loadAll = replays == null || !canSync.getAsBoolean() || !CameraHandler.isCameraEditorOpen();

        if (loadAll)
        {
            /* Display all replays */
            for (String record : records)
            {
                this.records.list.add(record);
            }
        }
        else
        {
            /* Display only current director block's replays */
            for (Replay replay : replays)
            {
                if (records.contains(replay.id) && !this.records.list.getList().contains(replay.id))
                {
                    this.records.list.getList().add(replay.id);
                }
            }
        }

        this.director = !loadAll;
        this.records.filter("", true);
        this.records.list.update();
    }

    @Override
    public void toggleVisible()
    {
        super.toggleVisible();

        this.panel.updateEditorWidth();
    }

    @Override
    public void setVisible(boolean visible)
    {
        super.setVisible(visible);

        this.panel.updateEditorWidth();
    }

    @Override
    public void draw(GuiContext context)
    {
        this.area.draw(0xff222222);
        GuiDraw.drawRect(this.area.x, this.area.y, this.area.ex(), this.area.y + 30, 0x44000000);

        GuiDraw.drawStringWithShadow(this.font, I18n.translate(this.director ? "blockbuster.gui.record_editor.directors" : "blockbuster.gui.record_editor.title"), this.area.x + 10, this.area.y + 11, 0xcccccc);

        super.draw(context);
    }
}
