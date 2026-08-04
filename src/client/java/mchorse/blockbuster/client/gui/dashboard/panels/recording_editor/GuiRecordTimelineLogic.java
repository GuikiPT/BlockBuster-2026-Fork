package mchorse.blockbuster.client.gui.dashboard.panels.recording_editor;

import mchorse.blockbuster.network.server.recording.actions.ServerHandlerActionsChange;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster_pack.morphs.SequencerMorph;
import mchorse.mclib.utils.ICopy;
import mchorse.mclib.utils.MathUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless logic core of the recording editor's action timeline (P138).
 *
 * <p>The full {@code GuiRecordTimeline} (legacy 1787-line GL widget) is not yet
 * ported — the 18 per-action editor sub-panels it registers land in P139 and its
 * rendering/input path rides the full McLib GL stack. What lives here is the
 * subset the plan (S12 P138 "Verification") calls out as headlessly testable and
 * behaviourally load-bearing: the <b>selection model</b> (anchor {@link #fromTick}
 * + gap-padded {@code List<List<Action>>} with trim/backfill) and the
 * <b>clipboard codec</b> ({@code "Actions"} / {@code "ActionType"} NBT). Every
 * method below is ported verbatim from
 * {@code blockbuster-1.12/.../recording_editor/GuiRecordTimeline.java} so the full
 * widget can later delegate to this class 1:1.</p>
 *
 * <p>Parity notes (load-bearing, do not "fix"):</p>
 * <ul>
 *   <li>Empty frames in a selection are {@code null} entries; {@link #isFrameEmpty}
 *       also treats an all-{@code null} list as empty.</li>
 *   <li>{@link #sortToOriginal} reorders a copied selection into the record's own
 *       action order so selection order never changes insertion order on
 *       copy/move.</li>
 *   <li>The clipboard is an in-memory {@link NbtCompound} — survives record
 *       switches, not sessions.</li>
 *   <li>Paste silently skips entries whose {@code "ActionType"} is unknown /
 *       fails to parse (never crashes — total-reader ground rule).</li>
 *   <li>Cell colour hue = {@code (ActionRegistry.getType(a) - 1) / getMaxID()} —
 *       registry byte IDs drive the timeline palette; see
 *       {@link RecordTimelineColors}.</li>
 * </ul>
 */
public class GuiRecordTimelineLogic
{
    /** The record being edited (legacy: {@code panel.record}). */
    public Record record;

    /**
     * Pointer to the current selected action. Can be {@code null}.
     */
    public Action current;

    /**
     * A frame in the list can be {@code null} if it does not contain any actions.
     */
    public final List<List<Action>> selection = new ArrayList<>();

    /**
     * The tick where the selection begins.
     */
    public int fromTick = -1;

    /**
     * The current selected tick. It should always be greater or equal to
     * {@link #fromTick}.
     */
    public Selection currentTick = new Selection(-1, -1);

    /**
     * Clipboard buffer (legacy: {@code panel.buffer}). In-memory NBT; survives
     * record switches but not sessions.
     */
    public NbtCompound buffer;

    /* ===================================================================== *
     *  Panel seams
     *
     *  The legacy widget reached straight into its owning panel
     *  ({@code this.panel.saveAction()}, {@code this.panel.selectAction(a)})
     *  and into its own {@code ScrollArea}s ({@code recalculateVertical()}).
     *  Those three call-sites are the ONLY non-headless things the legacy
     *  non-GL methods did, so they become callbacks here — everything else in
     *  this class is byte-for-byte legacy. {@code GuiRecordTimeline} installs
     *  them in its constructor.
     * ===================================================================== */

    /** Legacy {@code this.panel.saveAction()}. */
    public Runnable saveHook;

    /** Legacy {@code this.panel.selectAction(action)}. */
    public Consumer<Action> selectHook;

    /** Legacy {@code this.recalculateVertical()} (resizes the vertical scroll). */
    public Runnable verticalHook;

    public GuiRecordTimelineLogic()
    {}

    public GuiRecordTimelineLogic(Record record)
    {
        this.record = record;
    }

    /* ===================================================================== *
     *  Selection model
     * ===================================================================== */

    public boolean isFrameEmpty(List<Action> frame)
    {
        boolean empty = true;

        if (frame != null && !frame.isEmpty())
        {
            for (int a = 0; a < frame.size(); a++)
            {
                if (frame.get(a) != null)
                {
                    empty = false;
                }
            }
        }

        return empty;
    }

    /**
     * Strip empty frames from the beginning of a selection, returning the number
     * of frames removed (the shift the anchor must move by).
     */
    public int trimSelectionBeginning(List<List<Action>> selection)
    {
        int shift = 0;

        for (int start = 0; start < selection.size(); start++)
        {
            if (this.isFrameEmpty(selection.get(start)))
            {
                selection.remove(start);

                start--;
                shift++;
            }
            else
            {
                break;
            }
        }

        return shift;
    }

    /**
     * Trims the selection so that the first end frame that is not empty or null.
     * This method will remove entry from the given list, but it will not remove
     * entries from the list entries.
     *
     * @return the shift from the end
     */
    public int trimSelectionEnd(List<List<Action>> selection)
    {
        int shift = 0;

        for (int end = selection.size() - 1; end >= 0; end--)
        {
            if (this.isFrameEmpty(selection.get(end)))
            {
                selection.remove(end);

                shift++;
            }
            else
            {
                break;
            }
        }

        return shift;
    }

    /**
     * Add the given tick to the selection and close gaps to previous selection.
     * The provided tick will be greater or equal to {@link #fromTick} after this
     * operation.
     */
    public void selectTick(int tick)
    {
        int t = tick - this.fromTick;
        int start = (tick < this.fromTick) ? 0 : ((t >= this.selection.size()) ? this.selection.size() : 0);
        int end = (tick < this.fromTick) ? this.fromTick - tick : ((t >= this.selection.size()) ? t + 1 : 0);

        for (int i = start; i < end; i++)
        {
            this.selection.add(i, null);
        }

        if (tick < this.fromTick)
        {
            this.fromTick = tick;
        }
    }

    /**
     * Sort the given actions to the original actions list from the recording.
     * This should be used for example when copying or moving so the order of
     * selection doesn't change the order of the actions when inserted.
     */
    public void sortToOriginal(List<List<Action>> actions)
    {
        for (int tick = 0; tick < actions.size(); tick++)
        {
            if (actions.get(tick) != null && !actions.get(tick).isEmpty())
            {
                List<Action> frameList = new ArrayList<>();
                boolean added = false;

                for (int a = 0; a < actions.get(tick).size(); a++)
                {
                    int newIndex = this.record.getActionIndex(this.fromTick + tick, actions.get(tick).get(a));

                    if (newIndex != -1)
                    {
                        if (newIndex >= frameList.size())
                        {
                            frameList.addAll(Arrays.asList(new Action[newIndex - frameList.size() + 1]));
                        }

                        frameList.set(newIndex, actions.get(tick).get(a));

                        if (!added)
                        {
                            added = actions.get(tick).get(a) != null;
                        }
                    }
                }

                if (added)
                {
                    this.removeNulls(frameList);
                    actions.set(tick, frameList);
                }
            }
        }
    }

    private void removeNulls(List<Action> frame)
    {
        for (int s = 0, e = frame.size() - 1; s < frame.size() && e >= 0; s++, e--)
        {
            if (frame.get(e) == null)
            {
                frame.remove(e);
                e--;
            }

            if (frame.get(s) == null)
            {
                frame.remove(s);
                s--;
                e--;
            }

            if (s >= e) break;
        }
    }

    /**
     * This is used to determine whether you can escape out of the gui.
     *
     * @return true if this selection is not empty and if it does not contain
     *         only one empty frame.
     */
    public boolean isActive()
    {
        return !(this.selection.isEmpty() || (this.selection.size() == 1 && this.isFrameEmpty(this.selection.get(0))));
    }

    /**
     * @return true if the action at the tick and index is selected. False if
     *         tick is outside of selection, if there is no action at the tick
     *         and index or if the action is not in the selection.
     */
    public boolean isInSelection(int tick, int index)
    {
        if (tick < this.fromTick || this.selection.isEmpty() || this.fromTick == -1)
        {
            return false;
        }

        Action action = this.record.getAction(tick, index);
        int t = tick - this.fromTick;

        if (action == null || t >= this.selection.size())
        {
            return false;
        }

        return this.selection.get(t) != null ? this.selection.get(t).contains(action) : false;
    }

    /**
     * Identity search of an action across the whole selection — legacy inlined
     * this as a {@code selection.stream().anyMatch(...)} inside the morph time
     * handle click/release handlers.
     */
    public boolean isActionInSelection(Action action)
    {
        return this.selection.stream().anyMatch((frame) ->
        {
            if (frame != null && !frame.isEmpty())
            {
                return frame.stream().anyMatch((actionTest) -> actionTest == action);
            }

            return false;
        });
    }

    /**
     * Add the given actions to the selection starting at the specified tick.
     * Nothing will be added if the tick is outside the record.actions range or
     * if the specified actions are empty.
     */
    public void addToSelection(int tick, List<List<Action>> actions)
    {
        if (actions.isEmpty() || this.record.actions.size() <= tick || tick < 0)
        {
            return;
        }

        this.selectTick(tick);
        this.selectTick(tick + actions.size() - 1);

        int start = tick - this.fromTick;

        for (int i = start, c = 0; i < this.selection.size() && c < actions.size(); i++, c++)
        {
            if (this.selection.get(i) == null && actions.get(c) != null)
            {
                this.selection.set(i, new ArrayList<>(actions.get(c)));
            }
            else if (this.selection.get(i) != null && actions.get(c) != null)
            {
                this.selection.get(i).removeAll(actions.get(c));

                this.selection.get(i).addAll(actions.get(c));
            }
        }
    }

    /**
     * Remove the given tick and index from the selection and update
     * {@link #currentTick} and {@link #fromTick} if necessary.
     */
    public void removeFromSelection(int tick, int index)
    {
        if (tick < this.fromTick)
        {
            return;
        }

        int t = tick - this.fromTick;

        Action remove = this.record.getAction(tick, index);

        if (remove == null || t >= this.selection.size())
        {
            return;
        }

        if (this.selection.get(t) != null)
        {
            this.selection.get(t).remove(remove);
        }

        if (t == this.selection.size() - 1)
        {
            this.trimSelectionEnd(this.selection);
        }
        else if (t == 0)
        {
            this.fromTick += this.trimSelectionBeginning(this.selection);
        }
        else if (this.selection.get(t).isEmpty())
        {
            this.selection.set(t, null);
        }

        if (this.current == remove)
        {
            this.selectCurrentSaveOld(-1, -1);
        }
    }

    /**
     * Clear the selection, but keep current tick (without selecting a current
     * action) and save the old action.
     */
    public void deselect()
    {
        this.selection.clear();
        this.selectCurrentSaveOld(this.currentTick.getTick(), -1);
    }

    public void selectAll()
    {
        this.selection.clear();
        this.addToSelection(0, this.record.getActions(0, this.record.actions.size() - 1));
        this.selectCurrentSaveOld(this.currentTick.getTick(), this.currentTick.getIndex());
    }

    /**
     * Sets current action and {@link #currentTick} and updates the selection.
     * This method also updates the GUI action panel of the recording editor
     * panel (through {@link #selectHook}). If the selection was empty or if
     * {@link #fromTick} was -1 then {@link #fromTick} will be set to the
     * provided tick.
     */
    public void selectCurrent(int tick, int index)
    {
        Action selected = this.record.getAction(tick, index);

        if (selected != null)
        {
            if (this.fromTick == -1 || this.selection.isEmpty())
            {
                this.selection.clear();

                this.fromTick = tick;
                this.selection.add(null);
            }
            else
            {
                this.selectTick(tick);
            }

            int t = tick - this.fromTick;

            if (this.selection.get(t) != null)
            {
                if (!this.selection.get(t).contains(selected))
                {
                    this.selection.get(t).add(selected);
                }
            }
            else
            {
                this.selection.set(t, new ArrayList<>(Arrays.asList(selected)));
            }
        }
        else if (this.fromTick == -1 || this.selection.isEmpty())
        {
            this.selection.clear();

            this.fromTick = tick;
            this.selection.add(null);
        }

        this.current = selected;
        this.currentTick.set(tick, index);

        if (this.selectHook != null)
        {
            this.selectHook.accept(this.current);
        }
    }

    /**
     * {@link #selectCurrent(int, int)}, preceded by saving the previously
     * edited action back to the server ({@link #saveAction()}).
     */
    public void selectCurrentSaveOld(int tick, int index)
    {
        this.saveAction();

        this.selectCurrent(tick, index);
    }

    public void saveAction()
    {
        if (this.saveHook != null)
        {
            this.saveHook.run();
        }
    }

    /**
     * Legacy {@code reset()} minus the two {@code ScrollArea} calls (those stay
     * in the widget): re-anchor the selection inside the freshly loaded record.
     */
    public void reset()
    {
        if (this.record != null)
        {
            this.selection.clear();
            this.selectCurrent(MathUtils.clamp(this.fromTick, 0, this.record.actions.size() - 1), -1);
        }
    }

    /**
     * Legacy {@code recalculateVertical()}'s pure half: the vertical scroll's
     * item count is "most actions in any frame, plus one" (the extra row is the
     * always-available append slot). 0 when there is no record.
     */
    public int verticalSize()
    {
        int max = 0;

        if (this.record != null)
        {
            for (List<Action> actions : this.record.actions)
            {
                if (actions != null && actions.size() > max)
                {
                    max = actions.size();
                }
            }

            max += 1;
        }

        return max;
    }

    /* ===================================================================== *
     *  Structural edits (server-authoritative)
     *
     *  Every add/dupe/move/paste/delete goes through the S9 action-change
     *  packet family; the handler statics mutate the client record optimistically
     *  and then dispatch. Headless the dispatch is dropped by
     *  AbstractDispatcher (no client sender installed).
     * ===================================================================== */

    /**
     * Legacy split: index &lt; 0 uses the tick-only overload, otherwise the
     * tick+index one.
     */
    public void addActions(int tick, int index, List<List<Action>> actions)
    {
        if (index < 0)
        {
            ServerHandlerActionsChange.addActions(actions, this.record, tick);
        }
        else
        {
            ServerHandlerActionsChange.addActions(actions, this.record, tick, index);
        }

        this.recalculateVertical();
    }

    public void recalculateVertical()
    {
        if (this.verticalHook != null)
        {
            this.verticalHook.run();
        }
    }

    /**
     * Remove selected actions, send to server, clear selection and set current
     * to none.
     */
    public void removeActions()
    {
        if (this.fromTick == -1 || this.selection.isEmpty())
        {
            return;
        }

        List<List<Action>> selectionCopy = new ArrayList<>(this.selection);

        int startShift = this.trimSelectionBeginning(selectionCopy);
        this.trimSelectionEnd(selectionCopy);

        if (selectionCopy.isEmpty())
        {
            return;
        }

        int from = this.fromTick + startShift;

        List<List<Boolean>> deletionMask = this.record.getActionsMask(from, selectionCopy);

        this.saveAction();
        ServerHandlerActionsChange.deleteActions(this.record, from, deletionMask);

        /* deselect without saving previous action */
        this.selection.clear();
        this.selectCurrent(this.currentTick.getTick(), -1);

        this.recalculateVertical();
    }

    public void cutActions()
    {
        if (this.fromTick == -1 || this.selection.isEmpty())
        {
            return;
        }

        this.copyActions();
        this.removeActions();
    }

    /**
     * Paste the clipboard at {@link #currentTick}. A valid current index pastes
     * at that vertical index, otherwise the frames are appended; the pasted
     * content becomes the new selection with index -1.
     */
    public void pasteActions()
    {
        if (this.buffer == null || !this.buffer.contains("Actions") || this.currentTick.getTick() == -1)
        {
            return;
        }

        List<List<Action>> copied = this.parsePaste(this.buffer);

        if (copied.isEmpty())
        {
            return;
        }

        this.saveAction();

        if (this.currentTick.getIndex() < 0 || this.current == null)
        {
            this.addActions(this.currentTick.getTick(), -1, copied);
        }
        else
        {
            this.addActions(this.currentTick.getTick(), this.currentTick.getIndex(), copied);
        }

        this.selection.clear();
        this.selection.addAll(copied);
        this.selectCurrent(this.currentTick.getTick(), -1);
    }

    /**
     * Create a brand new action of the given registry name at the cursor.
     * Unknown names and out-of-range cursors are silently ignored.
     */
    public void createAction(String str)
    {
        if (!ActionRegistry.NAME_TO_CLASS.containsKey(str)
            || this.currentTick.getTick() < 0 || this.currentTick.getTick() >= this.record.actions.size())
        {
            return;
        }

        try
        {
            Action action = ActionRegistry.fromName(str);
            int tick = this.currentTick.getTick();
            int index = this.currentTick.getIndex();

            List<List<Action>> insert = new ArrayList<>();
            insert.add(new ArrayList<>(Arrays.asList(action)));

            this.addActions(tick, index, insert);
            this.selection.clear();
            this.selectCurrentSaveOld(tick, this.record.getActionIndex(tick, action));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Duplicate the selection in place: every action is cloned through an NBT
     * round-trip ({@code fromType(getType(a))} + {@code toNBT}/{@code fromNBT}),
     * inserted at the same {@link #fromTick}, and the copies become the new
     * selection. Per-action clone failures are swallowed (the original stays in
     * the copied list — legacy behaviour).
     */
    public void dupeActions()
    {
        if (this.fromTick < 0 || this.selection.isEmpty())
        {
            return;
        }

        int tick = this.fromTick;
        int index = this.currentTick.getIndex() == -1 ? 0 : (this.currentTick.getIndex() == -2 ? -1 : this.currentTick.getIndex());

        List<List<Action>> selectionCopy = new ArrayList<>(this.selection);

        if (selectionCopy.isEmpty())
        {
            return;
        }

        this.sortToOriginal(selectionCopy);
        int start = this.trimSelectionBeginning(selectionCopy);
        this.trimSelectionEnd(selectionCopy);

        Action newCurrent = this.cloneSelectionInPlace(selectionCopy);

        tick += start;

        this.addActions(tick, index, selectionCopy);

        this.selection.clear();
        this.addToSelection(tick, selectionCopy);
        this.selectCurrentSaveOld(this.currentTick.getTick(), this.record.getActionIndex(this.currentTick.getTick(), newCurrent));
    }

    /**
     * NBT round-trip clone of every action of a (already trimmed/sorted)
     * selection, in place.
     *
     * @return the clone of {@link #current}, or {@code null} when the current
     *         action was not part of the given selection
     */
    public Action cloneSelectionInPlace(List<List<Action>> selectionCopy)
    {
        Action newCurrent = null;

        for (int t = 0; t < selectionCopy.size(); t++)
        {
            if (selectionCopy.get(t) != null && !selectionCopy.get(t).isEmpty())
            {
                for (int a = 0; a < selectionCopy.get(t).size(); a++)
                {
                    if (selectionCopy.get(t).get(a) == null) continue;

                    try
                    {
                        Action newAction = ActionRegistry.fromType(ActionRegistry.getType(selectionCopy.get(t).get(a)));
                        NbtCompound tag = new NbtCompound();

                        selectionCopy.get(t).get(a).toNBT(tag);
                        newAction.fromNBT(tag);

                        if (selectionCopy.get(t).get(a) == this.current)
                        {
                            newCurrent = newAction;
                        }

                        selectionCopy.get(t).set(a, newAction);
                    }
                    catch (Exception e)
                    {}
                }
            }
        }

        return newCurrent;
    }

    /**
     * Move the whole selection so that the grabbed cell lands on
     * {@code tick}/{@code index}. The selection is removed first (server-side)
     * and re-added at the clamped destination; the previously current action
     * stays current.
     *
     * <p>The {@code -1} (before start) / {@code -2} (beyond end) sentinels of
     * {@code ScrollArea.getIndex} are special-cased exactly like legacy — see
     * the legacy TODO about their inconsistency; do not unify them.</p>
     *
     * @param lastClickedTick the tick the drag started on
     */
    public void moveSelectionTo(int tick, int index, int lastClickedTick)
    {
        /* beyond start */
        if (tick == -1)
        {
            tick = 0;
        }
        /* beyond end */
        else if (tick == -2)
        {
            tick = this.record.actions.size() - this.selection.size();
        }
        else
        {
            tick = tick + this.fromTick - lastClickedTick;
        }

        if (index == -1)
        {
            index = 0;
        }
        else if (index == -2)
        {
            index = this.record.actions.get(tick) == null ? 0 : this.record.actions.get(tick).size() - 1;
        }

        List<List<Action>> selectionCopy = new ArrayList<>(this.selection);

        this.sortToOriginal(selectionCopy);

        int start = this.trimSelectionBeginning(selectionCopy);
        this.trimSelectionEnd(selectionCopy);

        if (selectionCopy.isEmpty())
        {
            return;
        }

        Action current = this.current;
        int dT = this.currentTick.getTick() - this.fromTick;
        this.removeActions();
        this.selection.clear();
        this.selection.addAll(selectionCopy);

        tick += start;

        /* move it back if outside of range */
        if (tick < 0)
        {
            tick = 0;
        }
        else if (tick + this.selection.size() - 1 >= this.record.actions.size())
        {
            tick -= tick + this.selection.size() - 1 - this.record.actions.size() + 1;
        }

        this.fromTick = tick;
        this.currentTick.set(tick + dT - start, this.currentTick.getIndex());

        this.addActions(tick, index, selectionCopy);
        this.selectCurrent(this.currentTick.getTick(), this.record.getActionIndex(this.currentTick.getTick(), current));
    }

    /* ===================================================================== *
     *  Morph action animation length (duration bars / time handles)
     * ===================================================================== */

    /**
     * Animation length of a morph in ticks. {@link IAnimationProvider} morphs
     * report their {@code Animation.duration} (only when {@code animates}),
     * {@code SequencerMorph} reports its computed duration unless
     * {@code ignoreAnimationLengthUI} is set. Everything else: 0.
     */
    public static int getAnimationLength(AbstractMorph morph)
    {
        if (morph instanceof IAnimationProvider)
        {
            Animation animation = ((IAnimationProvider) morph).getAnimation();

            if (animation.animates)
            {
                return animation.duration;
            }
        }
        else if (morph instanceof SequencerMorph)
        {
            SequencerMorph sequencerMorph = (SequencerMorph) morph;

            return sequencerMorph.ignoreAnimationLengthUI ? 0 : (int) sequencerMorph.getDuration();
        }

        return 0;
    }

    /**
     * Deepest morph (itself or any of its body parts) with the longest
     * animation — that's the one whose duration the timeline bar renders.
     */
    public static AbstractMorph getMaxAnimationLengthMorph(AbstractMorph morph)
    {
        int ticks = getAnimationLength(morph);

        if (morph instanceof IBodyPartProvider)
        {
            BodyPartManager manager = ((IBodyPartProvider) morph).getBodyPart();

            for (BodyPart part : manager.parts)
            {
                if (!part.morph.isEmpty() && part.limb != null && !part.limb.isEmpty())
                {
                    AbstractMorph morph0 = getMaxAnimationLengthMorph(part.morph.get());
                    int ticks0 = getAnimationLength(morph0);

                    if (ticks0 > ticks)
                    {
                        ticks = ticks0;
                        morph = morph0;
                    }
                }
            }
        }

        return morph;
    }

    /**
     * Shift the animation duration of a morph AND of every (limbed, non-empty)
     * body part morph by {@code delta}, floored at 0.
     */
    public static void addAnimationDuration(AbstractMorph morph, int delta)
    {
        if (morph instanceof IAnimationProvider)
        {
            Animation animation = ((IAnimationProvider) morph).getAnimation();

            if (animation.animates)
            {
                animation.duration = Math.max(0, animation.duration + delta);
            }
        }

        if (morph instanceof IBodyPartProvider)
        {
            BodyPartManager manager = ((IBodyPartProvider) morph).getBodyPart();

            for (BodyPart part : manager.parts)
            {
                if (!part.morph.isEmpty() && part.limb != null && !part.limb.isEmpty())
                {
                    addAnimationDuration(part.morph.get(), delta);
                }
            }
        }
    }

    /**
     * Every {tick, index} whose {@link MorphAction}'s animation <b>ends</b>
     * inside the given rectangular tick/index range. The scan walks tick
     * <b>backwards</b> from {@code max(fromTick, toTick)} because a handle is
     * drawn at the far end of a bar that starts arbitrarily far to the left.
     */
    public List<int[]> getMorphActionHandlesRange(int fromTick, int toTick, int fromIndex, int toIndex)
    {
        if (fromTick < 0 || toTick < 0 || fromIndex < 0 || toIndex < 0)
        {
            return new ArrayList<>();
        }

        int tick0 = Math.min(fromTick, toTick);
        /* Clamped: the scroll index is actions.size() on the boundary pixel. */
        int tick1 = Math.min(Math.max(fromTick, toTick), this.record.actions.size() - 1);
        int index0 = Math.min(fromIndex, toIndex);
        int index1 = Math.max(fromIndex, toIndex);
        List<int[]> tickHandles = new ArrayList<>();

        for (int t = tick1; t >= 0; t--)
        {
            List<Action> actions = this.record.actions.get(t);

            if (actions == null || actions.isEmpty()) continue;

            for (int i = index0; i < actions.size() && i <= index1; i++)
            {
                if (!(actions.get(i) instanceof MorphAction)) continue;

                int ticks = getAnimationLength(getMaxAnimationLengthMorph(((MorphAction) actions.get(i)).morph));

                if (ticks <= 1) continue;
                int animationEndTick = t + ticks - 1;

                if (animationEndTick >= tick0 && animationEndTick <= tick1)
                {
                    tickHandles.add(new int[]{t, i});
                }
            }
        }

        return tickHandles;
    }

    /**
     * Apply a dragged time-handle delta to a set of morph actions and push each
     * changed action to the server.
     *
     * <p>Plain drag adds the delta recursively to the morph <b>and</b> its body
     * part morphs; Alt-drag only touches the single morph with the maximum
     * duration (the one the bar visualises). Floor is 0.</p>
     */
    public void applyTimeHandleDelta(Collection<MorphAction> handles, int diff, boolean alt)
    {
        if (diff == 0)
        {
            return;
        }

        for (MorphAction action : handles)
        {
            if (action.morph == null) continue;

            /* only change the animation length of the morph with maximum
             * duration, as this is what renders slider */
            if (alt)
            {
                AbstractMorph morph = getMaxAnimationLengthMorph(action.morph);

                if (morph instanceof IAnimationProvider)
                {
                    Animation animation = ((IAnimationProvider) morph).getAnimation();

                    if (animation.animates)
                    {
                        animation.duration = Math.max(0, animation.duration + diff);
                    }
                }
            }
            else
            {
                /* add the diff onto every morph, even bodyparts etc. */
                addAnimationDuration(action.morph, diff);
            }

            int[] found = this.record.findAction(action);
            ServerHandlerActionsChange.editAction(action, this.record, found[0], found[1]);
        }
    }

    /* ===================================================================== *
     *  Clipboard codec
     * ===================================================================== */

    /**
     * Serialise the current {@link #selection} into {@link #buffer} as an
     * {@code "Actions"} NBT list. Each per-frame list is an {@link NbtList} of
     * action compounds tagged with {@code "ActionType"} (the registry name) plus
     * the action's own {@code toNBT}. No-ops (and leaves {@link #buffer} unset)
     * when there is nothing selected — matching legacy {@code copyActions}.
     *
     * @return the buffer written, or {@code null} when nothing was copied.
     */
    public NbtCompound copyActions()
    {
        if (this.fromTick == -1 || this.selection.isEmpty())
        {
            return null;
        }

        List<List<Action>> selectionCopy = new ArrayList<>(this.selection);

        this.sortToOriginal(selectionCopy);

        this.trimSelectionBeginning(selectionCopy);
        this.trimSelectionEnd(selectionCopy);

        if (selectionCopy.isEmpty())
        {
            return null;
        }

        NbtList list = new NbtList();

        for (List<Action> frame : selectionCopy)
        {
            NbtList frameNBT = new NbtList();

            if (frame != null)
            {
                for (Action action : frame)
                {
                    if (action == null) continue;

                    NbtCompound actionNBT = new NbtCompound();

                    actionNBT.putString("ActionType", ActionRegistry.NAME_TO_CLASS.inverse().get(action.getClass()));
                    action.toNBT(actionNBT);
                    frameNBT.add(actionNBT);
                }
            }

            list.add(frameNBT);
        }

        this.buffer = new NbtCompound();
        this.buffer.put("Actions", list);

        return this.buffer;
    }

    /**
     * Parse a clipboard {@link NbtCompound} back into a gap-padded selection.
     * Frames become {@code null} when their NBT entry is not a list; individual
     * actions whose {@code "ActionType"} is missing/unknown or which fail to
     * parse are silently skipped. Mirrors the parsing half of legacy
     * {@code pasteActions} (the structural insert/select side effects belong to
     * the full widget). Result has its empty leading/trailing frames trimmed.
     */
    public List<List<Action>> parsePaste(NbtCompound buffer)
    {
        List<List<Action>> copied = new ArrayList<>();

        if (buffer == null || !buffer.contains("Actions"))
        {
            return copied;
        }

        NbtElement actions = buffer.get("Actions");

        if (actions instanceof NbtList)
        {
            NbtList nbtList = (NbtList) actions;

            for (int i = 0; i < nbtList.size(); i++)
            {
                NbtElement element = nbtList.get(i);

                if (!(element instanceof NbtList))
                {
                    copied.add(null);

                    continue;
                }

                List<Action> frame = null;

                NbtList frameNBT = (NbtList) element;

                for (int a = 0; a < frameNBT.size(); a++)
                {
                    NbtElement entry = frameNBT.get(a);

                    if (entry instanceof NbtCompound)
                    {
                        NbtCompound actionNBT = (NbtCompound) entry;

                        if (actionNBT.contains("ActionType"))
                        {
                            try
                            {
                                Action action = ActionRegistry.fromName(actionNBT.getString("ActionType"));

                                action.fromNBT(actionNBT);

                                if (frame == null)
                                {
                                    frame = new ArrayList<>();
                                }

                                frame.add(action);
                            }
                            catch (Exception e)
                            { }
                        }
                    }
                }

                copied.add(frame);
            }
        }

        this.trimSelectionBeginning(copied);
        this.trimSelectionEnd(copied);

        return copied;
    }

    /* ===================================================================== *
     *  Cursor tuple
     * ===================================================================== */

    /**
     * A {tick, index} cursor tuple. Ported verbatim from the legacy timeline's
     * inner {@code Selection} class (kept the name for diff-ability).
     */
    public static class Selection implements ICopy<Selection>
    {
        private int tick;
        private int index;

        public Selection(int tick, int index)
        {
            this.set(tick, index);
        }

        public void set(int tick, int index)
        {
            this.tick = tick;
            this.index = index;
        }

        public int getTick()
        {
            return this.tick;
        }

        public int getIndex()
        {
            return this.index;
        }

        @Override
        public Selection copy()
        {
            return new Selection(this.tick, this.index);
        }
    }
}
