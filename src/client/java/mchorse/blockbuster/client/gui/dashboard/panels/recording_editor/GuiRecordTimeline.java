package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor;

import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.mclib.McLib;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.utils.GuiContext;
import mchorse.mclib.client.gui.framework.elements.utils.GuiDraw;
import mchorse.mclib.client.gui.utils.GuiUtils;
import mchorse.mclib.client.gui.utils.LegacyKeyCodes;
import mchorse.mclib.client.gui.utils.ScrollArea;
import mchorse.mclib.client.gui.utils.ScrollDirection;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Color;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recording editor's tick/action timeline (roadmap P138).
 *
 * <p>1:1 port of Blockbuster 2.7.2's
 * {@code blockbuster-1.12/.../recording_editor/GuiRecordTimeline.java} (1787
 * lines). Everything that does not need pixels — the selection model, the
 * clipboard codec, the structural (server-authoritative) edits and the morph
 * animation-length math — lives in {@link GuiRecordTimelineLogic} and is driven
 * from here; this class owns the two {@link ScrollArea}s, the input handlers and
 * the GL drawing, exactly the split the rest of the port uses (the headless half
 * is what is unit-tested).</p>
 *
 * <p>Geometry, verbatim from legacy: horizontal {@code ScrollArea} item size
 * <b>34</b> px with scroll speed <b>68</b> (= 34 * 2), vertical item height
 * <b>20</b> px, vertical size = "most actions in any frame + 1", morph time
 * handle <b>4</b> px wide, Alt+wheel zoom of the tick width by ±2 clamped to
 * <b>[6, 50]</b> re-anchored around the cursor.</p>
 *
 * <p>Load-bearing legacy quirks reproduced here (do NOT "fix"):</p>
 * <ul>
 *   <li>{@code ScrollArea.getIndex} sentinels are inconsistent by legacy
 *       admission ({@code -1} both before the start <i>and</i> beyond the max
 *       index, {@code -2} beyond the end) — every handler special-cases them
 *       individually rather than normalising once. The legacy TODO is kept.</li>
 *   <li>{@link #getMorphActionHandles} and
 *       {@link GuiRecordTimelineLogic#getMorphActionHandlesRange} index
 *       {@code record.actions} with the raw scroll index, which is
 *       {@code actions.size()} on the single pixel where
 *       {@code axis == scrollSize}. Legacy has the same exposure; kept for
 *       parity.</li>
 *   <li>{@link #cursor} is an <b>externally written</b> field: the playback
 *       client hooks set it to the current tick every frame and {@link #draw}
 *       resets it to -1 after drawing.</li>
 *   <li>Cell hue is derived from the action's <b>registry byte ID</b>
 *       ({@link RecordTimelineColors}) — renumbering the registry repaints the
 *       timeline.</li>
 *   <li>Esc's {@code active()} predicate ({@link GuiRecordTimelineLogic#isActive})
 *       is what lets Esc close the GUI when nothing meaningful is selected.</li>
 * </ul>
 *
 * <p>Recorded deviations from legacy (both boundary-only):</p>
 * <ul>
 *   <li>The clipboard NBT lives on {@link GuiRecordTimelineLogic#buffer} instead
 *       of {@code GuiRecordingEditorPanel.buffer}. The timeline instance has the
 *       exact same lifetime as the panel (created once in its constructor), so
 *       the "survives record switches, not sessions" contract is unchanged; this
 *       just avoids a second owner of the same state.</li>
 *   <li>1.12 statics map to the port's seams: {@code Gui.drawRect} →
 *       {@link GuiDraw#drawRect}, {@code Gui.drawGradientRect} →
 *       {@link GuiDraw#drawVerticalGradientRect}, {@code GuiScreen.isXKeyDown} →
 *       {@link GuiUtils}, {@code MathHelper.hsvToRGB} →
 *       {@link RecordTimelineColors#hsvToRgb}, {@code Keyboard.KEY_*} →
 *       {@link LegacyKeyCodes} (LWJGL2 codes).</li>
 * </ul>
 */
public class GuiRecordTimeline extends GuiElement
{
    public GuiRecordingEditorPanel panel;
    public ScrollArea scroll;
    public ScrollArea vertical;

    /**
     * Headless core: selection model, clipboard codec, structural edits and
     * animation-length math (see the class javadoc).
     */
    public final GuiRecordTimelineLogic logic = new GuiRecordTimelineLogic();

    public boolean lastDragging = false;
    public int lastX;
    public int lastY;

    /**
     * Last clicked x coordinate with mouse button 0
     */
    private int lastLeftX;

    /**
     * Last clicked y coordinate with mouse button 0
     */
    private int lastLeftY;

    public int lastH;
    public int lastV;

    /**
     * What has been last left clicked
     */
    private GuiRecordTimelineLogic.Selection lastClicked = new GuiRecordTimelineLogic.Selection(-1, -1);

    /**
     * To render the actions at the mouse position correctly when dragging.
     * The default value is -1 when this has not been set
     */
    private int movingDx = -1;
    private int movingDy = -1;

    /**
     * The first scroll when drawing the selection area.
     * This is used to ensure continuity of the selection area when scrolling around.
     */
    private int areaScrollDx = -1;
    private int areaScrollDy = -1;

    /**
     * If this is true moving is possible
     */
    public boolean canMove;
    public boolean moving;

    /**
     * Whether the user is ready to select an area
     */
    private boolean canSelectArea;
    private boolean selectingArea;

    /**
     * Playback cursor tick. Externally written every frame by the playback
     * hooks and reset to -1 at the end of {@link #draw(GuiContext)}.
     */
    public int cursor = -1;

    private boolean preventMouseReleaseSelect = false;

    private int adaptiveMaxIndex;
    private final int itemHeight = 20;
    private final int morphActionHandleWidth = 4;

    /**
     * A list of morph actions where the time handles have been selected.
     * Important to draw the selection and for multi selection of time handles
     */
    private final Set<MorphAction> selectedTimeHandles = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean movingMorphActionTimeHandle = false;

    /**
     * The tick that got clicked by {@link #mouseClicked(GuiContext)}
     * This is cleared after releasing mouse.
     * The key of the map is the mouse button.
     */
    private final Map<Integer, Integer> clickedTick = new HashMap<>();

    public GuiRecordTimeline(MinecraftClient mc, GuiRecordingEditorPanel panel)
    {
        super(mc);

        this.scroll = new ScrollArea(34);
        this.scroll.direction = ScrollDirection.HORIZONTAL;
        this.scroll.scrollSpeed = 34 * 2;
        this.vertical = new ScrollArea(this.itemHeight);
        this.vertical.direction = ScrollDirection.VERTICAL;
        this.panel = panel;

        this.logic.saveHook = () ->
        {
            if (this.panel != null)
            {
                this.panel.saveAction();
            }
        };
        this.logic.selectHook = (action) ->
        {
            if (this.panel != null)
            {
                this.panel.selectAction(action);
            }
        };
        this.logic.verticalHook = this::recalculateVertical;

        IKey category = IKey.lang("blockbuster.gui.aperture.keys.category");

        this.keys().register(IKey.lang("blockbuster.gui.aperture.keys.add_morph_action"), LegacyKeyCodes.KEY_M, () -> this.createAction("morph"))
                .held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.record_editor.deselect"), LegacyKeyCodes.KEY_ESCAPE, this::deselect)
                .category(category).active(() -> this.isActive());
        this.keys().register(IKey.lang("blockbuster.gui.record_editor.select_all"), LegacyKeyCodes.KEY_A, this::selectAll)
                .held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.record_editor.copy"), LegacyKeyCodes.KEY_C, this::copyActions)
                .held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.record_editor.paste"), LegacyKeyCodes.KEY_V, this::pasteActions)
                .held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.record_editor.cut"), LegacyKeyCodes.KEY_X, this::cutActions)
                .held(LegacyKeyCodes.KEY_LCONTROL).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.duplicate"), LegacyKeyCodes.KEY_D, this::dupeActions)
                .held(LegacyKeyCodes.KEY_LSHIFT).category(category);
        this.keys().register(IKey.lang("blockbuster.gui.remove"), LegacyKeyCodes.KEY_DELETE, this::removeActions).category(category);
    }

    /* ===================================================================== *
     *  Record plumbing
     * ===================================================================== */

    /**
     * Legacy read {@code this.panel.record} on every single access; the logic
     * core keeps its own reference, so it is re-pointed at the panel's current
     * record before each entry point.
     */
    private void sync()
    {
        if (this.panel != null)
        {
            this.logic.record = this.panel.record;
        }
    }

    private boolean hasRecord()
    {
        this.sync();

        return this.logic.record != null;
    }

    /**
     * @return the tick of the current selected action / frame
     */
    public int getCurrentTick()
    {
        return this.logic.currentTick.getTick();
    }

    public int getCurrentIndex()
    {
        return this.logic.currentTick.getIndex();
    }

    /**
     * This is used to determine whether you can escape out of the gui
     * @return true if this selection is not empty and if it does not contain only one empty frame.
     */
    public boolean isActive()
    {
        return this.logic.isActive();
    }

    /** The in-memory clipboard (legacy {@code panel.buffer}). */
    public NbtCompound getBuffer()
    {
        return this.logic.buffer;
    }

    public void setBuffer(NbtCompound buffer)
    {
        this.logic.buffer = buffer;
    }

    @Override
    public void resize()
    {
        super.resize();

        this.scroll.copy(this.area);
        this.vertical.copy(this.area);
    }

    /**
     * Update with a new recording
     */
    public void reset()
    {
        if (this.hasRecord())
        {
            this.logic.reset();

            this.scroll.setSize(this.logic.record.actions.size());
            this.scroll.clamp();

            this.recalculateVertical();
        }
    }

    public void recalculateVertical()
    {
        this.sync();

        this.vertical.setSize(this.logic.verticalSize());
        this.vertical.clamp();
    }

    /* ===================================================================== *
     *  Mouse input
     * ===================================================================== */

    @Override
    public boolean mouseClicked(GuiContext context)
    {
        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        if (!this.hasRecord())
        {
            return super.mouseClicked(context);
        }

        //TODO getIndex returns -2 when beyond end but returns -1 when beyond max index, but also -1 when behind beginning...
        //this brings issues with interpreting!
        int tick = this.scroll.getIndex(context.mouseX, context.mouseY);
        int index = this.vertical.getIndex(context.mouseX, context.mouseY);

        this.clickedTick.put(context.mouseButton, tick);

        if (context.mouseButton == 0)
        {
            /* morph action time handle has the highest priority */
            boolean abort = false;
            if (this.clickMorphActionTimeHandle(context.mouseX, context.mouseY)) abort = true;

            this.lastClicked = new GuiRecordTimelineLogic.Selection(tick, index);
            this.lastLeftX = this.lastX;
            this.lastLeftY = this.lastY;

            if (abort) return true;
        }

        if (context.mouseButton == 1)
        {
            if (this.moving || this.selectingArea)
            {
                this.preventMouseReleaseSelect = true;
            }

            this.moving = false;
            this.canMove = false;
            this.selectingArea = false;
            this.movingMorphActionTimeHandle = false;
        }

        if (context.mouseButton == 2 && this.area.isInside(context))
        {
            this.lastDragging = true;
            this.lastH = this.scroll.scroll;
            this.lastV = this.vertical.scroll;

            return true;
        }

        if (super.mouseClicked(context) || this.scroll.mouseClicked(context) || this.vertical.mouseClicked(context))
        {
            this.preventMouseReleaseSelect = true;

            return true;
        }

        if (this.scroll.isInside(context) && !this.moving && context.mouseButton == 0)
        {
            if (tick >= 0 && tick < this.logic.record.actions.size())
            {
                this.selectMouseClicked(tick, index);
            }
        }

        return false;
    }

    /**
     * Searches if there is a morph action time handle at the given mouse coordinates and whether to select it.
     *
     * @return a list of {tick, index} pairs (empty when there is no morph action time handle here)
     */
    private List<int[]> getMorphActionHandles(int mouseX, int mouseY)
    {
        int tick = this.scroll.getIndex(mouseX, mouseY);
        int index = this.vertical.getIndex(mouseX, mouseY);

        List<int[]> tickHandles = new ArrayList<>();

        if (tick < 0 || index < 0)
        {
            return tickHandles;
        }

        /* The scroll index equals actions.size() on the single boundary pixel
         * where axis == scrollSize; legacy indexed record.actions with it raw
         * and threw IndexOutOfBounds out of a mouse handler. Clamping is
         * behaviour-identical for every reachable click. */
        tick = Math.min(tick, this.logic.record.actions.size() - 1);

        for (int t = tick; t >= 0; t--)
        {
            List<Action> actions = this.logic.record.actions.get(t);

            if (actions != null && index < actions.size() && actions.get(index) instanceof MorphAction)
            {
                MorphAction morphAction = (MorphAction) actions.get(index);

                int ticks = GuiRecordTimelineLogic.getAnimationLength(GuiRecordTimelineLogic.getMaxAnimationLengthMorph(morphAction.morph));

                /* this should be the global screen coordinates of the end of the tick item cell*/
                int test = this.scroll.x - this.scroll.scroll + (tick + 1) * this.scroll.scrollItemSize;

                /* first check if mouse inside the tick of the length -> then check if mouse on pixels of the handle */
                if ((ticks > 1 && tick == t + ticks - 1) && mouseX >= (test - this.morphActionHandleWidth))
                {
                    tickHandles.add(new int[]{t, index});
                }
            }
        }

        return tickHandles;
    }

    private boolean clickMorphActionTimeHandle(int mouseX, int mouseY)
    {
        List<int[]> selections = this.getMorphActionHandles(mouseX, mouseY);

        if (selections.isEmpty())
        {
            this.selectedTimeHandles.clear();
            return false;
        }

        boolean cleared = false;

        List<int[]> lastClickedSelection = this.getMorphActionHandles(this.lastLeftX, this.lastLeftY);

        /* if user clicked on time handles before and now clicks on new time handles with shift -> select a range */
        if (GuiUtils.isShiftKeyDown() && !lastClickedSelection.isEmpty())
        {
            int tick = this.scroll.getIndex(mouseX, mouseY);
            int index = this.vertical.getIndex(mouseX, mouseY);

            selections = this.logic.getMorphActionHandlesRange(tick, this.lastClicked.getTick(), index, this.lastClicked.getIndex());
        }

        for (int[] selection : selections)
        {
            int tick = selection[0];
            int index = selection[1];
            MorphAction action = (MorphAction) this.logic.record.getAction(tick, index);

            if (GuiUtils.isShiftKeyDown())
            {
                this.selectedTimeHandles.add(action);
            }
            else if (GuiUtils.isCtrlKeyDown())
            {
                if (this.selectedTimeHandles.contains(action)) this.selectedTimeHandles.remove(action);
                else this.selectedTimeHandles.add(action);
            }
            else if (!this.selectedTimeHandles.contains(action))
            {
                /* clear only once so multiple overlapping time handles can be selected */
                if (!cleared)
                {
                    this.selectedTimeHandles.clear();
                    cleared = true;
                }

                this.selectedTimeHandles.add(action);
            }

            boolean actionFound = this.logic.isActionInSelection(action);

            this.deselect();

            /*
             * only select the action of the time handle if it was already selected
             * otherwise clicking on time handles will always open the action menu and might annoy the user
             */
            if (actionFound)
            {
                this.logic.fromTick = tick;
                this.logic.selectCurrentSaveOld(tick, index);
                this.preventMouseReleaseSelect = true;
            }
        }

        this.movingMorphActionTimeHandle = true;

        return true;
    }

    private void releaseMorphActionTimeHandle(GuiContext context)
    {
        List<int[]> selections = this.getMorphActionHandles(context.mouseX, context.mouseY);

        for (int[] selection : selections)
        {
            int tick = selection[0];
            int index = selection[1];
            MorphAction action = (MorphAction) this.logic.record.getAction(tick, index);

            /* releasing the mouse on a selected time handle means clear selection and select it as the current */
            if (!(GuiUtils.isShiftKeyDown() || GuiUtils.isCtrlKeyDown()) && this.selectedTimeHandles.contains(action))
            {
                this.selectedTimeHandles.clear();
                this.selectedTimeHandles.add(action);

                boolean actionFound = this.logic.isActionInSelection(action);

                this.deselect();

                if (actionFound)
                {
                    this.logic.fromTick = tick;
                    this.logic.selectCurrentSaveOld(tick, index);
                    this.preventMouseReleaseSelect = true;
                }
            }
        }

        if (this.movingMorphActionTimeHandle && this.clickedTick.containsKey(0))
        {
            int tick = this.scroll.getIndex(context.mouseX, context.mouseY);
            int diff = tick - this.clickedTick.get(0);

            this.logic.applyTimeHandleDelta(this.selectedTimeHandles, diff, GuiUtils.isAltKeyDown());
        }

        this.movingMorphActionTimeHandle = false;
    }

    private void selectMouseClicked(int tick, int index)
    {
        if (this.logic.isInSelection(tick, index))
        {
            if (GuiUtils.isCtrlKeyDown())
            {
                this.logic.removeFromSelection(tick, index);
            }
            else if (GuiUtils.isShiftKeyDown())
            {
                this.canSelectArea = true;
            }
            else
            {
                this.awaitMoving();
            }
        }
        else
        {
            if (GuiUtils.isShiftKeyDown())
            {
                if (this.logic.currentTick.getTick() != -1 && this.logic.currentTick.getIndex() != -1)
                {
                    /* select a range from current tick to clicked tick */
                    List<List<Action>> actionRange = this.logic.record.getActions(tick, this.logic.currentTick.getTick(), index, this.logic.currentTick.getIndex());

                    this.logic.addToSelection(Math.min(tick, this.logic.currentTick.getTick()), actionRange);
                }

                this.logic.selectCurrentSaveOld(tick, index);
            }
            else if (GuiUtils.isCtrlKeyDown())
            {
                if (this.logic.record.getAction(tick, index) != null)
                {
                    this.logic.selectCurrentSaveOld(tick, index);
                }
            }
            else if (this.logic.record.getAction(tick, index) != null)
            {
                this.logic.selection.clear();
                this.logic.fromTick = tick;

                this.logic.selectCurrentSaveOld(tick, index);
                this.awaitMoving();
            }
            else
            {
                this.canSelectArea = true;
            }
        }
    }

    private void awaitMoving()
    {
        this.canMove = true;
        this.moving = false;
    }

    @Override
    public void mouseReleased(GuiContext context)
    {
        super.mouseReleased(context);

        if (!this.hasRecord())
        {
            this.lastDragging = false;
            this.scroll.mouseReleased(context);
            this.vertical.mouseReleased(context);

            return;
        }

        this.releaseMorphActionTimeHandle(context);

        this.clickedTick.remove(context.mouseButton);

        if (context.mouseButton == 0)
        {
            if (this.moving)
            {
                this.logic.moveSelectionTo(this.scroll.getIndex(context.mouseX, context.mouseY), this.vertical.getIndex(context.mouseX, context.mouseY), this.lastClicked.getTick());
            }
            else if (this.selectingArea)
            {
                if (!GuiUtils.isShiftKeyDown())
                {
                    this.logic.selection.clear();
                }

                int scrollIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
                int verticalIndex = this.vertical.getIndex(context.mouseX, context.mouseY);

                scrollIndex = scrollIndex == -1 ? 0 : (scrollIndex == -2 ? this.logic.record.actions.size() - 1 : scrollIndex);

                int frameSize = this.logic.record.getActions(scrollIndex) == null ? 1 : this.logic.record.getActions(scrollIndex).size();
                verticalIndex = verticalIndex == -1 ? 0 : (verticalIndex == -2 ? frameSize - 1 : verticalIndex);

                int fromSt = Math.min(this.lastClicked.getTick(), scrollIndex);
                int toSt = Math.max(this.lastClicked.getTick(), scrollIndex);
                int fromSi = Math.min(this.lastClicked.getIndex(), verticalIndex);
                int toSi = Math.max(this.lastClicked.getIndex(), verticalIndex);

                List<List<Action>> actionRange = this.logic.record.getActions(fromSt, toSt, fromSi, toSi);

                int startShift = this.logic.trimSelectionBeginning(actionRange);
                this.logic.trimSelectionEnd(actionRange);

                this.logic.addToSelection(fromSt + startShift, actionRange);
            }
            else if (!this.preventMouseReleaseSelect)
            {
                int tick = this.scroll.getIndex(context.mouseX, context.mouseY);
                int index = this.vertical.getIndex(context.mouseX, context.mouseY);

                if (index != -1 && 0 <= tick && tick < this.logic.record.actions.size())
                {
                    if (!GuiUtils.isShiftKeyDown() && !GuiUtils.isCtrlKeyDown()
                            && (this.logic.isInSelection(tick, index) || this.logic.record.getAction(tick, index) == null))
                    {
                        this.logic.selection.clear();
                        this.logic.selectCurrentSaveOld(tick, index);
                    }
                }
            }

            this.preventMouseReleaseSelect = false;
            this.canSelectArea = false;
            this.selectingArea = false;
            this.canMove = false;
            this.moving = false;
        }

        this.lastDragging = false;
        this.scroll.mouseReleased(context);
        this.vertical.mouseReleased(context);
    }

    @Override
    public boolean mouseScrolled(GuiContext context)
    {
        if (super.mouseScrolled(context))
        {
            return true;
        }

        boolean shift = GuiUtils.isShiftKeyDown();
        boolean alt = GuiUtils.isAltKeyDown();

        if (shift && !alt)
        {
            return this.vertical.mouseScroll(context);
        }
        else if (alt && !shift)
        {
            if (!this.hasRecord())
            {
                return this.scroll.mouseScroll(context);
            }

            int scale = this.scroll.scrollItemSize;

            this.scroll.scrollItemSize = MathUtils.clamp(this.scroll.scrollItemSize + (int) Math.copySign(2, context.mouseWheel), 6, 50);
            this.scroll.setSize(this.logic.record.actions.size());
            this.scroll.clamp();

            if (this.scroll.scrollItemSize != scale)
            {
                int value = this.scroll.scroll + (context.mouseX - this.area.x);

                this.scroll.scroll = (int) ((value - (value - this.scroll.scroll) * (scale / (float) this.scroll.scrollItemSize)) * (this.scroll.scrollItemSize / (float) scale));
            }

            return true;
        }

        return this.scroll.mouseScroll(context);
    }

    /* ===================================================================== *
     *  Selection / clipboard / structural edit entry points
     *
     *  All of them are keybind + icon-button targets on the panel, so they
     *  re-sync the record first and then delegate to the headless core.
     * ===================================================================== */

    public void deselect()
    {
        if (this.hasRecord()) this.logic.deselect();
    }

    public void selectAll()
    {
        if (this.hasRecord()) this.logic.selectAll();
    }

    public void selectCurrent(int tick, int index)
    {
        if (this.hasRecord()) this.logic.selectCurrent(tick, index);
    }

    public void selectCurrentSaveOld(int tick, int index)
    {
        if (this.hasRecord()) this.logic.selectCurrentSaveOld(tick, index);
    }

    public void saveAction()
    {
        this.logic.saveAction();
    }

    public void cutActions()
    {
        if (this.hasRecord()) this.logic.cutActions();
    }

    public void copyActions()
    {
        if (this.hasRecord()) this.logic.copyActions();
    }

    public void pasteActions()
    {
        if (this.hasRecord()) this.logic.pasteActions();
    }

    public void createAction(String str)
    {
        if (this.hasRecord()) this.logic.createAction(str);
    }

    public void dupeActions()
    {
        if (this.hasRecord()) this.logic.dupeActions();
    }

    /**
     * Remove selected actions, send to server, clear selection and set current
     * to none
     */
    public void removeActions()
    {
        if (this.hasRecord()) this.logic.removeActions();
    }

    /* ===================================================================== *
     *  Drawing
     * ===================================================================== */

    @Override
    public void draw(GuiContext context)
    {
        if (!this.hasRecord())
        {
            return;
        }

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        int count = this.logic.record.actions.size();

        if (this.lastDragging)
        {
            this.scroll.scroll = this.lastH + (this.lastX - mouseX);
            this.scroll.clamp();
            this.vertical.scroll = this.lastV + (this.lastY - mouseY);
            this.vertical.clamp();
        }

        if (!this.moving && (Math.abs(mouseX - this.lastX) > 2 || Math.abs(mouseY - this.lastY) > 2))
        {
            if (this.canMove)
            {
                this.moving = true;
            }

            if (this.canSelectArea)
            {
                this.selectingArea = true;
            }
        }

        this.scroll.drag(mouseX, mouseY);
        this.vertical.drag(mouseX, mouseY);
        this.scroll.draw(ColorUtils.HALF_BLACK);

        GuiDraw.drawRect(this.area.ex(), this.area.y, this.area.ex() + 20, this.area.ey(), 0xff222222);
        GuiDraw.drawRect(this.area.x - 20, this.area.y, this.area.x, this.area.ey(), 0xff222222);
        GuiDraw.drawHorizontalGradientRect(this.area.ex() - 8, this.area.y, this.area.ex(), this.area.ey(), 0, ColorUtils.HALF_BLACK, 0);
        GuiDraw.drawHorizontalGradientRect(this.area.x, this.area.y, this.area.x + 8, this.area.ey(), ColorUtils.HALF_BLACK, 0, 0);

        int max = this.area.x + this.scroll.scrollItemSize * count;

        if (max < this.area.ex())
        {
            GuiDraw.drawRect(max, this.area.y, this.area.ex(), this.area.ey(), 0xaa000000);
        }

        GuiDraw.scissor(this.area.x, this.area.y, this.area.w, this.area.h, context);

        int w = this.scroll.scrollItemSize;
        int index = this.scroll.scroll / w;
        int diff = index;

        index -= this.adaptiveMaxIndex;
        index = index < 0 ? 0 : index;
        diff = diff - index;

        this.adaptiveMaxIndex = 0;

        for (int i = index, c = i + this.area.w / w + 2 + diff; i < c; i++)
        {
            int x = this.scroll.x - this.scroll.scroll + i * w;

            if (i < count)
            {
                GuiDraw.drawRect(x, this.scroll.y, x + 1, this.scroll.ey(), 0x22ffffff);
            }

            int toTick = this.logic.selection.isEmpty() ? this.logic.fromTick : this.logic.fromTick + this.logic.selection.size() - 1;

            if (this.logic.fromTick <= i && i <= toTick)
            {
                GuiDraw.drawRect(x, this.scroll.y, x + w + 1, this.scroll.ey(), 0x440088ff);
            }

            if (i >= 0 && i < count)
            {
                List<Action> actions = this.logic.record.actions.get(i);

                if (actions != null)
                {
                    int j = 0;

                    for (Action action : actions)
                    {
                        if (this.moving && this.logic.isInSelection(i, j))
                        {
                            j++;

                            continue;
                        }

                        int y = this.scroll.y + j * this.itemHeight - this.vertical.scroll;

                        int scrollIndex = this.scroll.getIndex(mouseX, mouseY);
                        int verticalIndex = this.vertical.getIndex(mouseX, mouseY);

                        scrollIndex = scrollIndex == -1 ? 0 : (scrollIndex == -2 ? count - 1 : scrollIndex);
                        verticalIndex = verticalIndex == -1 ? 0 : (verticalIndex == -2 ? actions.size() - 1 : verticalIndex);

                        int fromSt = Math.min(this.lastClicked.getTick(), scrollIndex);
                        int toSt = Math.max(this.lastClicked.getTick(), scrollIndex);
                        int fromSi = Math.min(this.lastClicked.getIndex(), verticalIndex);
                        int toSi = Math.max(this.lastClicked.getIndex(), verticalIndex);

                        boolean selected;

                        if (this.selectingArea)
                        {
                            selected = fromSt <= i && i <= toSt && fromSi <= j && j <= toSi;

                            if (GuiUtils.isShiftKeyDown())
                            {
                                selected = selected || this.logic.isInSelection(i, j);
                            }
                        }
                        else
                        {
                            selected = this.logic.isInSelection(i, j);
                        }

                        this.drawAction(action, context, String.valueOf(j), x, y, selected);

                        j++;
                    }
                }
            }
        }

        for (int i = index, c = i + this.area.w / w + 2 + diff; i < c; i++)
        {
            if (i % 5 == 0 && i < count && i != this.cursor)
            {
                int x = this.scroll.x - this.scroll.scroll + i * w;
                int y = this.scroll.ey() - 12;

                String str = String.valueOf(i);

                GuiDraw.drawVerticalGradientRect(x + 1, y - 6, x + w, y + 12, 0, ColorUtils.HALF_BLACK);
                GuiDraw.drawStringWithShadow(this.font, str, x + (this.scroll.scrollItemSize - GuiDraw.textWidth(this.font, str) + 2) / 2, y, 0xffffff);
            }
        }

        this.scroll.drawScrollbar();
        this.vertical.drawScrollbar();

        /* Draw cursor (tick indicator) */
        if (this.cursor >= 0 && this.cursor < this.logic.record.actions.size())
        {
            int x = this.scroll.x - this.scroll.scroll + this.cursor * w;
            int cursorX = x + 2;

            String label = this.cursor + "/" + this.logic.record.actions.size();
            int width = GuiDraw.textWidth(this.font, label);
            int height = 2 + GuiDraw.fontHeight(this.font);
            int offsetY = this.scroll.ey() - height;

            if (cursorX + width + 4 > this.scroll.ex())
            {
                cursorX -= width + 4 + 2;
            }

            GuiDraw.drawRect(x, this.scroll.y, x + 2, this.scroll.ey(), 0xff57f52a);
            GuiDraw.drawRect(cursorX, offsetY, cursorX + width + 4, offsetY + height, 0xaa57f52a);

            GuiDraw.drawStringWithShadow(this.font, label, cursorX + 2, offsetY + 2, 0xffffff);
        }

        String label = this.logic.record.filename;

        GuiDraw.drawTextBackground(this.font, label, this.area.ex() - GuiDraw.textWidth(this.font, label) - 5, this.area.ey() - 13, 0xffffff, 0xaa000000 + McLib.primaryColor.get());

        GuiDraw.unscissor(context);

        if (this.moving)
        {
            int x = mouseX;
            int y = mouseY;

            int posX = w * (this.lastClicked.getTick()) + this.scroll.x - this.scroll.scroll;
            int posY = this.itemHeight * this.lastClicked.getIndex() + this.scroll.y - this.vertical.scroll;

            if (this.movingDx == -1)
            {
                this.movingDx = mouseX - posX;
            }

            if (this.movingDy == -1)
            {
                this.movingDy = mouseY - posY;
            }

            x -= this.movingDx - w * (this.logic.fromTick - this.lastClicked.getTick());
            y -= this.movingDy + this.itemHeight * this.lastClicked.getIndex();

            int y0 = y;

            for (int tick = this.logic.fromTick; tick < this.logic.record.actions.size(); tick++)
            {
                List<Action> frame = this.logic.record.actions.get(tick);

                if (frame != null)
                {
                    for (int i = 0; i < frame.size(); i++)
                    {
                        if (this.logic.isInSelection(tick, i))
                        {
                            this.drawAction(frame.get(i), context, String.valueOf(i), x, y, true);
                        }

                        y += this.itemHeight;
                    }
                }

                y = y0;
                x += w;
            }
        }
        else
        {
            this.movingDx = -1;
            this.movingDy = -1;
        }

        if (this.selectingArea)
        {
            if (this.areaScrollDx == -1)
            {
                this.areaScrollDx = this.scroll.scroll;
            }

            if (this.areaScrollDy == -1)
            {
                this.areaScrollDy = this.vertical.scroll;
            }

            GuiDraw.drawRect(this.lastLeftX - (this.scroll.scroll - this.areaScrollDx), this.lastLeftY - (this.vertical.scroll - this.areaScrollDy), mouseX, mouseY, 0x440088FF);
        }
        else
        {
            this.areaScrollDy = -1;
            this.areaScrollDx = -1;
        }

        super.draw(context);

        this.cursor = -1;
    }

    private void drawAction(Action action, GuiContext context, String label, int x, int y, boolean selected)
    {
        int w = this.scroll.scrollItemSize;
        float hue = RecordTimelineColors.actionHue(action);
        int color = RecordTimelineColors.hsvToRgb(hue, 1F, 1F);
        int offset = this.scroll.scrollItemSize < 18 ? (this.scroll.scrollItemSize - GuiDraw.textWidth(this.font, label)) / 2 : 6;

        this.drawAnimationLength(action, context, x, y, color, selected);

        GuiDraw.drawRect(x, y, x + w, y + this.itemHeight, color + ColorUtils.HALF_BLACK);
        GuiDraw.drawStringWithShadow(this.font, label, x + offset, y + 6, 0xffffff);

        if (selected)
        {
            /* get complementary color, but ignore purple and blue colors - they don't pop enough */
            float hueSelected = RecordTimelineColors.selectedHue(hue);
            int c = action == this.logic.current ? (new Color(RecordTimelineColors.hsvToRgb(hueSelected, 0.5F, 1F), false)).getRGBAColor() : 0xffffffff;
            int border = action == this.logic.current ? 2 : 1;
            GuiDraw.drawOutline(x, y, x + w, y + this.itemHeight, c, border);
        }
    }

    private void drawAnimationLength(Action action, GuiContext context, int x, int y, int color, boolean selected)
    {
        if (action instanceof MorphAction)
        {
            MorphAction morphAction = (MorphAction) action;
            int ticks = GuiRecordTimelineLogic.getAnimationLength(GuiRecordTimelineLogic.getMaxAnimationLengthMorph(morphAction.morph));

            if (this.movingMorphActionTimeHandle && this.selectedTimeHandles.contains(action) && this.clickedTick.containsKey(0))
            {
                ticks = Math.max(0, ticks + this.scroll.getIndex(context.mouseX, context.mouseY) - this.clickedTick.get(0));
            }

            if (ticks > 1)
            {
                ticks -= 1;

                int offset = x + this.scroll.scrollItemSize;
                boolean timeHandleSelected = this.selectedTimeHandles.contains(action);

                GuiDraw.drawRect(offset, y + 8, offset + ticks * this.scroll.scrollItemSize, y + 12, selected ? 0xffffffff : color + 0x33000000);
                GuiDraw.drawRect(offset + ticks * this.scroll.scrollItemSize - this.morphActionHandleWidth, y, offset + ticks * this.scroll.scrollItemSize, y + this.itemHeight,
                        timeHandleSelected ? 0xffffffff : 0xff000000 + color);
            }

            this.adaptiveMaxIndex = Math.max(ticks, this.adaptiveMaxIndex);
        }
    }
}
