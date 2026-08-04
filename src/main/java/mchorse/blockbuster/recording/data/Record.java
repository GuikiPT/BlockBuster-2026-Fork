package mchorse.blockbuster.recording.data;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.aperture.CameraHandler;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.actions.ActionRegistry;
import mchorse.blockbuster.recording.actions.MorphAction;
import mchorse.blockbuster.recording.actions.MountingAction;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.blockbuster.utils.EntityUtils;
import mchorse.mclib.utils.AtomicWrite;
import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.PastCopies;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This class stores actions and frames states for a recording (to be played
 * back or while recording) — roadmap P102, 1:1 port of the 2.7.2 class.
 *
 * There's two list arrays in this class, index in both of these arrays
 * represents the frame position (0 is first frame). Frames list is always
 * populated, but actions list will contain some nulls.
 *
 * <p>Deferred to later stages (seams kept): {@code applyPreviousMorph} and
 * the morph scrub path (S4 bundled Metamorph), client-side frame application
 * details (S10/S15), EntityActor-specific state (S8 P93).</p>
 */
public class Record
{
    public static final FoundAction ACTION = new FoundAction();
    public static final MorphAction MORPH = new MorphAction();

    /**
     * Signature of the recording. This is the NBT {@code Version} short
     * inside the compound — not a raw leading file short.
     */
    public static final short SIGNATURE = 148;

    /**
     * Filename of this record
     */
    public String filename;

    /**
     * Version of this record
     */
    public short version = SIGNATURE;

    /**
     * Pre-delay same thing as post-delay but less useful
     */
    public int preDelay = 0;

    /**
     * Post-delay allows actors to stay longer on the screen before
     * whoosing into void
     */
    public int postDelay = 0;

    /**
     * Recorded actions.
     * The list contains every frame of the recording.
     * If no action is present at a frame, the frame will be null.
     */
    public List<List<Action>> actions = new ArrayList<List<Action>>();

    /**
     * Recorded frames
     */
    public List<Frame> frames = new ArrayList<Frame>();

    /**
     * Player data which was recorded when player started recording.
     * Opaque compound — never run vanilla datafixers on it.
     */
    public NbtCompound playerData;

    /**
     * Unload timer. Used only on server side.
     */
    public int unload;

    /**
     * Whether this record has changed elements
     */
    public boolean dirty;

    /**
     * Can be null
     */
    private Replay replay;

    public Record(String filename)
    {
        this.filename = filename;
        this.resetUnload();
    }

    /**
     * Set this replay to the reference of the provided replay
     */
    public void setReplay(Replay replay)
    {
        this.replay = replay;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    /**
     * Get the full length (including post and pre delays) of this record in frames/ticks
     */
    public int getFullLength()
    {
        return this.preDelay + this.getLength() + this.postDelay;
    }

    /**
     * Get the length of this record in frames/ticks
     */
    public int getLength()
    {
        return Math.max(this.actions.size(), this.frames.size());
    }

    /**
     * @return actions at the given tick. Can return null if nothing is present there.
     */
    public List<Action> getActions(int tick)
    {
        if (tick >= this.actions.size() || tick < 0)
        {
            return null;
        }

        return this.actions.get(tick);
    }

    /**
     * @return action at the given tick and index, or null when out of bounds.
     */
    public Action getAction(int tick, int index)
    {
        List<Action> actions = this.getActions(tick);

        if (actions != null && index >= 0 && index < actions.size())
        {
            return actions.get(index);
        }

        return null;
    }

    /**
     * If fromIndex0 and toIndex0 are both -1 every action at the frame in the range will be added.
     */
    public List<List<Action>> getActions(int fromTick0, int toTick0, int fromIndex0, int toIndex0)
    {
        int fromIndex = Math.min(fromIndex0, toIndex0);
        int toIndex = Math.max(fromIndex0, toIndex0);
        int fromTick = Math.min(fromTick0, toTick0);
        int toTick = Math.max(fromTick0, toTick0);

        if (fromTick0 < 0 || toTick0 < 0 || ((fromIndex0 != -1 || toIndex0 != -1) && fromIndex0 < 0 && toIndex0 < 0)
            || toTick >= this.actions.size())
        {
            return new ArrayList<>();
        }

        List<List<Action>> actionRange = this.actions.subList(fromTick, toTick + 1);

        if (actionRange != null)
        {
            actionRange = new ArrayList<>(actionRange);

            for (int i = 0; i < actionRange.size(); i++)
            {
                List<Action> frame = actionRange.get(i);

                if (frame != null && !frame.isEmpty())
                {
                    if (fromIndex == -1 && toIndex == -1)
                    {
                        actionRange.set(i, new ArrayList<>(frame));
                    }
                    else if (fromIndex >= frame.size())
                    {
                        actionRange.set(i, null);
                    }
                    else
                    {
                        int i0 = MathUtils.clamp(fromIndex, 0, frame.size() - 1);
                        int i1 = MathUtils.clamp(toIndex + 1, 0, frame.size());

                        actionRange.set(i, new ArrayList<>(frame.subList(i0, i1)));
                    }
                }
                else
                {
                    actionRange.set(i, null);
                }
            }
        }

        return actionRange;
    }

    public List<List<Action>> getActions(int fromTick0, int toTick0)
    {
        return this.getActions(fromTick0, toTick0, -1, -1);
    }

    /**
     * @return convert the given action list into a boolean mask, which can be used for deletion.
     *         Returns an empty list if the specified tick is out of range.
     */
    public List<List<Boolean>> getActionsMask(int fromTick, List<List<Action>> actions)
    {
        if (fromTick < 0 || fromTick >= this.actions.size())
        {
            return new ArrayList<>();
        }

        List<List<Boolean>> mask = new ArrayList<>();

        for (int t = fromTick; t < this.actions.size() && t - fromTick < actions.size(); t++)
        {
            List<Boolean> maskFrame = new ArrayList<>();

            if (actions.get(t - fromTick) != null && this.actions.get(t) != null && !this.actions.get(t).isEmpty())
            {
                for (int a = 0; a < this.actions.get(t).size(); a++)
                {
                    maskFrame.add(actions.get(t - fromTick).contains(this.actions.get(t).get(a)));
                }
            }
            else
            {
                maskFrame.add(false);
            }

            mask.add(maskFrame);
        }

        return mask;
    }

    /**
     * @return the index of the provided action at the provided tick, or -1.
     */
    public int getActionIndex(int tick, Action action)
    {
        if (tick < 0 || tick >= this.actions.size()
            || this.actions.get(tick) == null || this.actions.get(tick).isEmpty() || action == null)
        {
            return -1;
        }

        for (int a = 0; a < this.actions.get(tick).size(); a++)
        {
            if (this.actions.get(tick).get(a) == action)
            {
                return a;
            }
        }

        return -1;
    }

    /**
     * @return int array {tick, index} of the found action. If nothing was found the values will be -1
     */
    public int[] findAction(Action action)
    {
        if (action == null)
        {
            return new int[]{-1, -1};
        }

        for (int t = 0; t < this.actions.size(); t++)
        {
            int i = this.getActionIndex(t, action);

            if (i != -1) return new int[]{t, i};
        }

        return new int[]{-1, -1};
    }

    /**
     * Get frame on given tick
     */
    public Frame getFrame(int tick)
    {
        if (tick >= this.frames.size() || tick < 0)
        {
            return null;
        }

        return this.frames.get(tick);
    }

    /**
     * Reset unloading timer
     */
    public void resetUnload()
    {
        this.unload = Blockbuster.recordUnloadTime.get();
    }

    public void applyFrame(int tick, LivingEntity actor, boolean force)
    {
        this.applyFrame(tick, actor, force, false);
    }

    /**
     * Apply a frame at given tick on the given actor.
     */
    public void applyFrame(int tick, LivingEntity actor, boolean force, boolean realPlayer)
    {
        if (tick >= this.frames.size() || tick < 0)
        {
            return;
        }

        Frame frame = this.frames.get(tick);

        frame.apply(actor, this.replay, force);

        if (realPlayer)
        {
            actor.refreshPositionAndAngles(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
            actor.setVelocity(frame.motionX, frame.motionY, frame.motionZ);

            actor.setOnGround(frame.onGround);

            if (frame.hasBodyYaw)
            {
                actor.bodyYaw = frame.bodyYaw;
            }

            if (actor.getWorld().isClient)
            {
                applyClientMovement(actor, frame);
            }

            actor.setSneaking(frame.isSneaking);
            actor.setSprinting(frame.isSprinting);

            if (actor.getWorld().isClient)
            {
                applyFrameClient(actor, null, frame);
            }
        }

        if (actor.getWorld().isClient && Blockbuster.actorFixY.get())
        {
            actor.setPos(actor.getX(), frame.y, actor.getZ());
        }

        Frame prev = this.frames.get(Math.max(0, tick - 1));

        if (realPlayer || !actor.getWorld().isClient)
        {
            actor.lastRenderX = prev.x;
            actor.lastRenderY = prev.y;
            actor.lastRenderZ = prev.z;
            actor.prevX = prev.x;
            actor.prevY = prev.y;
            actor.prevZ = prev.z;

            actor.prevYaw = prev.yaw;
            actor.prevPitch = prev.pitch;
            actor.prevHeadYaw = prev.yawHead;

            if (prev.hasBodyYaw)
            {
                actor.prevBodyYaw = prev.bodyYaw;
            }

            if (actor.getWorld().isClient)
            {
                applyFrameClient(actor, prev, frame);
            }
        }
        else if (actor instanceof EntityActor)
        {
            ((EntityActor) actor).prevRoll = prev.roll;
        }

        /* Override fall distance, apparently fallDistance gets reset
         * faster than RecordRecorder can record both onGround and
         * fallDistance being correct for player, so we just hack */
        actor.fallDistance = prev.fallDistance;

        if (tick < this.frames.size() - 1)
        {
            Frame next = this.frames.get(tick + 1);

            /* Walking sounds */
            if (actor instanceof PlayerEntity)
            {
                double dx = next.x - frame.x;
                double dy = next.y - frame.y;
                double dz = next.z - frame.z;

                actor.horizontalSpeed = actor.horizontalSpeed + MathHelper.sqrt((float) (dx * dx + dz * dz)) * 0.32F;
                actor.distanceTraveled = actor.distanceTraveled + MathHelper.sqrt((float) (dx * dx + dy * dy + dz * dz)) * 0.32F;
            }
        }
    }

    /**
     * S22 P238 seam — legacy {@code @SideOnly(CLIENT) applyClientMovement} set
     * {@code ((EntityPlayerSP) actor).movementInput.sneak = frame.isSneaking}.
     * {@code ClientPlayerEntity}/{@code Input} are client-only classes and this
     * is main-source, so the client entrypoint
     * ({@code mchorse.blockbuster.client.LimbRollWiring}) installs the write.
     * Unset ⇒ no-op, which is the correct server-side behaviour.
     */
    public static BiConsumer<LivingEntity, Boolean> clientSneakInput = (actor, sneaking) -> {};

    /**
     * Legacy {@code Record.applyClientMovement} — replay the recorded sneak
     * <b>input</b> on the local player, not just the sneaking flag. Without it
     * a played-back local player stands upright between the flag being set and
     * the next input poll clearing it.
     */
    /* Package-visible (legacy: private) so RecordClientFrameTest can pin them
     * without a world — applyFrame itself needs one. */
    static void applyClientMovement(LivingEntity actor, Frame frame)
    {
        clientSneakInput.accept(actor, frame.isSneaking);
    }

    /**
     * Legacy {@code Record.applyFrameClient} — when the replayed entity is the
     * local player, push the recorded camera roll into Aperture
     * ({@code CameraHandler.setRoll(prevRoll, roll)}). Called twice per applied
     * frame exactly like legacy: once with {@code prev == null} (both ends of
     * the lerp are this frame's roll) from the {@code realPlayer} block, and
     * once with the previous frame from the interpolation block.
     */
    static void applyFrameClient(LivingEntity actor, Frame prev, Frame frame)
    {
        if (actor != null && actor == EntityUtils.clientPlayer.get())
        {
            CameraHandler.setRoll(prev == null ? frame.roll : prev.roll, frame.roll);
        }
    }

    public Frame getFrameSafe(int tick)
    {
        if (this.frames.isEmpty())
        {
            return null;
        }

        return this.frames.get(MathUtils.clamp(tick, 0, this.frames.size() - 1));
    }

    /**
     * Apply an action at the given tick on the given actor. Don't pass tick
     * value less than 0, otherwise you might experience game crash.
     */
    public void applyAction(int tick, LivingEntity actor)
    {
        this.applyAction(tick, actor, false);
    }

    /**
     * Apply an action at the given tick on the given actor. Don't pass tick
     * value less than 0, otherwise you might experience game crash.
     */
    public void applyAction(int tick, LivingEntity actor, boolean safe)
    {
        if (tick >= this.actions.size() || tick < 0)
        {
            return;
        }

        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            for (Action action : actions)
            {
                if (safe && !action.isSafe())
                {
                    continue;
                }

                try
                {
                    action.apply(actor);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Seek the nearest morph action
     */
    public FoundAction seekMorphAction(int tick, MorphAction last)
    {
        /* I hope it won't cause a lag...  */
        int threshold = 0;

        boolean canRet = last == null;

        while (tick >= threshold)
        {
            List<Action> actions = this.actions.get(tick);

            if (actions == null)
            {
                tick--;

                continue;
            }

            for (int i = actions.size() - 1; i >= 0; i--)
            {
                Action action = actions.get(i);

                if (!canRet && action == last)
                {
                    canRet = true;
                }
                else if (canRet && action instanceof MorphAction)
                {
                    ACTION.set(tick, (MorphAction) action);

                    return ACTION;
                }
            }

            tick--;
        }

        return null;
    }

    /**
     * Apply the morph the actor should be wearing at {@code tick} — the scrub
     * path, run whenever playback jumps rather than advances (spawn data,
     * seeking, a scene goto).
     *
     * <p>Walks back from {@code tick} to the most recent {@link MorphAction}.
     * When that morph is an {@link mchorse.metamorph.api.morphs.ISyncableMorph}
     * and previews are paused, it also walks back one more to find the morph
     * <em>before</em> it, so the two can be handed to
     * {@link MorphAction#applyWithOffset} with the tick distances that let an
     * animated morph resume mid-animation instead of restarting. Otherwise it
     * is a plain {@link MorphAction#apply}.</p>
     *
     * <p>With no morph action in range the replay's own morph stands in, via
     * the shared scratch {@link #MORPH} action (legacy reused one instance;
     * kept, because it is also what makes the three branches share
     * {@code MorphAction}'s ISyncableMorph handling).</p>
     *
     * <p>Two legacy quirks are load-bearing: the early return when {@code tick}
     * is past the end of the action list ("stay at the last morph" — without it
     * a finished record snaps back to the replay morph), and the swallowed
     * exception around the found-action branch, which keeps a single broken
     * morph from breaking playback.</p>
     */
    public void applyPreviousMorph(LivingEntity actor, Replay replay, int tick, MorphType type)
    {
        boolean pause = type != MorphType.REGULAR && Blockbuster.recordPausePreview.get();
        AbstractMorph replayMorph = replay == null ? null : replay.morph;

        /* when the tick is at the end - do not apply replay's morph - stay at the last morph */
        if (tick >= this.actions.size())
        {
            return;
        }

        FoundAction found = this.seekMorphAction(tick, null);

        if (found != null)
        {
            try
            {
                MorphAction action = found.action;

                if (pause && action.morph instanceof ISyncableMorph)
                {
                    int foundTick = found.tick;
                    int offset = tick - foundTick;

                    found = this.seekMorphAction(foundTick, action);
                    AbstractMorph previous = found == null ? replayMorph : found.action.morph;
                    int previousOffset = foundTick - (found == null ? 0 : found.tick);

                    action.applyWithOffset(actor, offset, previous, previousOffset, type == MorphType.FORCE);
                }
                else
                {
                    action.apply(actor);
                }
            }
            catch (Exception e)
            {
                Blockbuster.LOGGER.warn("Record '" + this.filename + "': failed to apply the previous morph at tick " + tick, e);
            }
        }
        else if (replay != null)
        {
            if (pause && replay.morph != null)
            {
                MORPH.morph = replay.morph;
                MORPH.applyWithOffset(actor, tick, null, 0, type == MorphType.FORCE);
            }
            else if (type == MorphType.FORCE && replay.morph != null)
            {
                MORPH.morph = replay.morph;
                MORPH.applyWithForce(actor);
            }
            else
            {
                replay.apply(actor);
            }
        }
    }

    /**
     * Reset the actor based on this record
     */
    public void reset(LivingEntity actor)
    {
        if (actor.hasVehicle())
        {
            this.resetMount(actor);
        }

        if (actor.getHealth() > 0.0F)
        {
            this.applyFrame(0, actor, true);

            /* Reseting actor's state */
            actor.setSneaking(false);
            actor.setSprinting(false);
            actor.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            actor.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
            actor.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
            actor.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
            actor.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            actor.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    /**
     * Reset actor's mount
     */
    protected void resetMount(LivingEntity actor)
    {
        int index = -1;

        /* Find at which tick player has mounted a vehicle */
        for (int i = 0, c = this.actions.size(); i < c; i++)
        {
            List<Action> actions = this.actions.get(i);

            if (actions == null)
            {
                continue;
            }

            for (Action action : actions)
            {
                if (action instanceof MountingAction)
                {
                    MountingAction act = (MountingAction) action;

                    if (act.isMounting)
                    {
                        index = i + 1;
                        break;
                    }
                }
            }
        }

        actor.stopRiding();

        if (index != -1)
        {
            Frame frame = this.frames.get(index);

            if (frame != null)
            {
                Entity mount = actor.getVehicle();

                /* P241: never teleport an actor-on-actor mount — a mounted
                 * EntityActor has its own record driving its position, and
                 * yanking it to the rider's frame would fight that playback
                 * (legacy `mount != null && !(mount instanceof EntityActor)`).
                 *
                 * Legacy quirk kept verbatim: `stopRiding()` above already
                 * nulled the vehicle (1.12.2 `dismountRidingEntity()` did the
                 * same before `getRidingEntity()`), so in practice `mount` is
                 * always null here and the teleport never fires. The guard is
                 * carried anyway so the two trees read identically and a future
                 * reorder of the dismount cannot silently resurrect the bug. */
                if (mount != null && !(mount instanceof EntityActor))
                {
                    mount.refreshPositionAndAngles(frame.x, frame.y, frame.z, frame.yaw, frame.pitch);
                }
            }
        }
    }

    /**
     * Add an action to the record
     */
    public void addAction(int tick, Action action)
    {
        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            actions.add(action);
        }
        else
        {
            actions = new ArrayList<Action>();
            actions.add(action);

            this.actions.set(tick, actions);
        }
    }

    /**
     * Add an action to the recording at the specified tick and index.
     * If the index is greater than the frame's size at the specified tick,
     * the action will just be appended to the actions of the frame.
     */
    public void addAction(int tick, int index, Action action)
    {
        List<Action> actions = this.actions.get(tick);

        if (actions != null)
        {
            if (index == -1 || index > actions.size())
            {
                actions.add(action);
            }
            else
            {
                actions.add(index, action);
            }
        }
        else
        {
            actions = new ArrayList<Action>();
            actions.add(action);

            this.actions.set(tick, actions);
        }
    }

    /**
     * Adds many ticks of actions beginning at the provided fromTick.
     */
    public void addActionCollection(int tick, List<List<Action>> actions)
    {
        this.addActionCollection(tick, -1, actions);
    }

    /**
     * Add an action collection beginning at the specified tick at the specified index.
     * If the index is -1, the actions at the frames will just be added on top.
     */
    public void addActionCollection(int tick, int index, List<List<Action>> actions)
    {
        if (index < -1 || tick < 0 || tick >= this.actions.size() || actions == null)
        {
            return;
        }

        for (int i = tick; i < this.actions.size() && i - tick < actions.size(); i++)
        {
            List<Action> frame = this.actions.get(i);
            List<Action> actionFrame = actions.get(i - tick) != null && !actions.get(i - tick).isEmpty() ? new ArrayList<>(actions.get(i - tick)) : null;

            if (frame == null)
            {
                this.actions.set(i, actionFrame);
            }
            else if (actionFrame != null)
            {
                if (index > frame.size() || index == -1)
                {
                    frame.addAll(actionFrame);
                }
                else
                {
                    frame.addAll(index, actionFrame);
                }
            }
        }
    }

    public void addActions(int tick, List<Action> actions)
    {
        if (tick < 0 || tick >= this.actions.size())
        {
            return;
        }

        List<Action> present = this.actions.get(tick);

        if (present == null)
        {
            this.actions.set(tick, actions);
        }
        else if (actions != null)
        {
            present.addAll(actions);
        }
    }

    /**
     * Remove an action at given tick and index
     */
    public void removeAction(int tick, int index)
    {
        if (index == -1)
        {
            this.actions.set(tick, null);
        }
        else
        {
            List<Action> actions = this.actions.get(tick);

            if (index >= 0 && index < actions.size())
            {
                actions.remove(index);

                if (actions.isEmpty())
                {
                    this.actions.set(tick, null);
                }
            }
        }
    }

    public void removeActions(int fromTick, List<List<Action>> actions)
    {
        for (int tick = fromTick, c = 0; tick < this.actions.size() && c < actions.size(); tick++, c++)
        {
            if (this.actions.get(tick) != null && actions.get(c) != null)
            {
                this.actions.get(tick).removeAll(actions.get(c));
            }

            if (this.actions.get(tick) != null && this.actions.get(tick).isEmpty())
            {
                this.actions.set(tick, null);
            }
        }
    }

    /**
     * Remove actions based on the boolean mask provided.
     */
    public void removeActionsMask(int fromTick, List<List<Boolean>> mask)
    {
        for (int tick = fromTick, c = 0; tick < this.actions.size() && c < mask.size(); tick++, c++)
        {
            if (this.actions.get(tick) != null && mask.get(c) != null)
            {
                List<Action> remove = new ArrayList<>();

                for (int a = 0; a < this.actions.get(tick).size() && a < mask.get(c).size(); a++)
                {
                    if (mask.get(c).get(a))
                    {
                        remove.add(this.actions.get(tick).get(a));
                    }
                }

                this.actions.get(tick).removeAll(remove);
            }
        }
    }

    /**
     * Remove actions from tick and to tick inclusive and at every tick
     * remove from index to index inclusive.
     * If both index parameters are -1, all actions at the respective ticks will be deleted.
     */
    public void removeActions(int fromTick0, int toTick0, int fromIndex0, int toIndex0)
    {
        int fromIndex = Math.min(fromIndex0, toIndex0);
        int toIndex = Math.max(fromIndex0, toIndex0);
        int fromTick = Math.min(fromTick0, toTick0);
        int toTick = Math.max(fromTick0, toTick0);
        int frameCount = this.actions.size();

        if (fromIndex == -1 && toIndex == -1)
        {
            for (int tick = fromTick; tick <= toTick && tick < frameCount; tick++)
            {
                this.actions.set(tick, null);
            }
        }
        else
        {
            for (int tick = fromTick; tick <= toTick && tick < frameCount; tick++)
            {
                List<Action> actions = this.actions.get(tick);

                if (actions == null) continue;

                if (fromIndex != -1 && toIndex != -1)
                {
                    int max = toIndex;

                    while (fromIndex <= max && fromIndex < actions.size())
                    {
                        actions.remove(fromIndex);

                        max--;
                    }
                }
                else
                {
                    int index = fromIndex == -1 ? toIndex : fromIndex;

                    if (index < actions.size())
                    {
                        actions.remove(index);
                    }
                }

                if (actions.isEmpty())
                {
                    this.actions.set(tick, null);
                }
            }
        }
    }

    /**
     * Replace an action at given tick and index
     */
    public void replaceAction(int tick, int index, Action action)
    {
        if (tick < 0 || tick >= this.actions.size())
        {
            return;
        }

        List<Action> actions = this.actions.get(tick);

        if (actions == null || index < 0 || index >= actions.size())
        {
            this.addAction(tick, action);
        }
        else
        {
            actions.set(index, action);
        }
    }

    /**
     * Create a copy of this record. Clones actions via NBT + registry — this
     * normalizes transient state; do not "optimize" to field copies.
     */
    @Override
    public Record clone()
    {
        Record record = new Record(this.filename);

        record.version = this.version;
        record.preDelay = this.preDelay;
        record.postDelay = this.postDelay;

        for (Frame frame : this.frames)
        {
            record.frames.add(frame.copy());
        }

        for (List<Action> actions : this.actions)
        {
            if (actions == null || actions.isEmpty())
            {
                record.actions.add(null);
            }
            else
            {
                List<Action> newActions = new ArrayList<Action>();

                for (Action action : actions)
                {
                    try
                    {
                        NbtCompound tag = new NbtCompound();

                        action.toNBT(tag);

                        Action newAction = ActionRegistry.fromType(ActionRegistry.getType(action));

                        newAction.fromNBT(tag);
                        newActions.add(newAction);
                    }
                    catch (Exception e)
                    {
                        System.out.println("Failed to clone an action!");
                        e.printStackTrace();
                    }
                }

                record.actions.add(newActions);
            }
        }

        return record;
    }

    public boolean save(File file) throws IOException
    {
        return this.save(file, true);
    }

    public boolean save(File file, boolean savePast) throws IOException
    {
        return this.save(file, savePast, false);
    }

    /**
     * Save a recording to given file.
     *
     * This method basically writes the signature of the current version,
     * and then saves all available frames and actions. Key insertion order
     * matches legacy statement order (byte-parity, P118).
     *
     * <p><b>P284 deviation from 1.12.2 (destructive-save guard).</b> A record
     * that holds <b>zero frames</b> will not be written over an on-disk record
     * that holds frames — see {@link #acceptSave(File, String, boolean)}. Pass
     * {@code allowEmptyOverwrite} to opt a call site out.</p>
     *
     * @return whether the file was written. {@code false} means the guard
     *         refused and <b>nothing on disk was touched</b> — in particular the
     *         {@code .dat~N} chain was <i>not</i> rotated.
     */
    public boolean save(File file, boolean savePast, boolean allowEmptyOverwrite) throws IOException
    {
        if (!allowEmptyOverwrite && !acceptSave(file, this.filename, this.frames.isEmpty()))
        {
            return false;
        }

        if (savePast && file.isFile())
        {
            this.savePastCopies(file);
        }

        NbtCompound compound = new NbtCompound();
        NbtList frames = new NbtList();

        /* Version of the recording */
        compound.putShort("Version", SIGNATURE);
        compound.putInt("PreDelay", this.preDelay);
        compound.putInt("PostDelay", this.postDelay);
        compound.put("Actions", this.createActionMap());

        if (this.playerData != null)
        {
            compound.put("PlayerData", this.playerData);
        }

        int c = this.frames.size();
        int d = this.actions.size() - this.frames.size();

        if (d < 0) d = 0;

        for (int i = 0; i < c; i++)
        {
            NbtCompound frameTag = new NbtCompound();

            Frame frame = this.frames.get(i);
            List<Action> actions = null;

            if (d + i <= this.actions.size() - 1)
            {
                actions = this.actions.get(d + i);
            }

            frame.toNBT(frameTag);

            if (actions != null)
            {
                NbtList actionsTag = new NbtList();

                for (Action action : actions)
                {
                    NbtCompound actionTag = new NbtCompound();

                    action.toNBT(actionTag);
                    actionTag.putByte("Type", ActionRegistry.CLASS_TO_ID.get(action.getClass()));
                    actionsTag.add(actionTag);
                }

                frameTag.put("Action", actionsTag);
            }

            frames.add(frameTag);
        }

        compound.put("Frames", frames);

        /* P284: legacy handed NbtIo a FileOutputStream on the destination,
         * which truncates <name>.dat before the first byte of the new
         * recording exists. Written to a sibling temp file and moved into
         * place instead — same bytes, but a crash or a full disk mid-write
         * leaves the previous recording intact rather than a truncated one. */
        AtomicWrite.write(file, stream -> NbtIo.writeCompressed(compound, stream));

        return true;
    }

    /* ------------------------------------------------------------------ *
     * P284 — the destructive-save guard
     * ------------------------------------------------------------------ */

    /**
     * Whether a save may proceed (roadmap <b>P284</b>).
     *
     * <p><b>The bug this closes.</b> S22 P279 stopped <i>one</i> route by which
     * an empty recording replaced a good one — the {@code PacketFramesChunk}
     * upload. The route was never the problem; the writer was. {@code Record
     * .save} has seventeen call sites (every {@code /record} sub-command, the
     * scene-dupe handler, the ranged-overwrite handler, and
     * {@code RecordManager.reset()}'s dirty flush at world unload), and each one
     * of them would happily serialize a zero-frame {@link Record} over a take
     * the user cannot re-perform.</p>
     *
     * <p><b>And the backup chain does not save them.</b> {@link
     * #savePastCopies(File)} rotates <i>on every save</i>, empty or not: it
     * deletes {@code .dat~5} and shifts everything down one. Five consecutive
     * empty saves therefore evict every good copy. This is not hypothetical —
     * the reporter's own world (read on 2026-07-26) has
     * {@code E_1.dat} at <b>0, 0, 0, 226, 99</b> frames across
     * {@code .dat}/{@code ~1}/{@code ~2}/{@code ~3}/{@code ~4}: three empty
     * saves have already pushed the last good take to the second-to-last slot,
     * and {@code R_1.dat}'s real 2076-frame take is sitting in {@code ~5}, one
     * save away from deletion. Refusing the write refuses the rotation with it,
     * which is the half that actually preserves the user's work.</p>
     *
     * <p><b>Deliberate deviation from 1.12.2</b>, in the shape P279 set as the
     * precedent: refuse the destructive write and log loudly by name rather than
     * silently repair. 2.7.2 wrote unconditionally, so a legacy-faithful port
     * would too; this is a case the plan explicitly licenses departing from,
     * because the destroyed content is not regenerable. One legacy path becomes
     * a refusal rather than a silent wipe: {@code /record cut <name> 0 0}
     * evaluates {@code frames.subList(0, 0)} and empties the record. That was
     * always a bug wearing a success message.</p>
     *
     * <p>The disk read only happens on the empty-in-memory path, so a normal
     * save pays nothing. A file that cannot be read is <b>not</b> protected:
     * there is nothing recoverable in it, and blocking the write would trap the
     * user with a record they can neither play nor replace.</p>
     *
     * <p>Static and public so the policy is headless-testable without a
     * {@link Record} instance.</p>
     *
     * @param file the destination.
     * @param filename the record's name, for the log line.
     * @param empty whether the record about to be written has zero frames.
     * @return whether the write may proceed.
     */
    public static boolean acceptSave(File file, String filename, boolean empty)
    {
        if (!empty || file == null || !file.isFile())
        {
            return true;
        }

        int existing = countFrames(file);

        if (existing <= 0)
        {
            return true;
        }

        Blockbuster.LOGGER.warn(
            "Refusing to save recording '{}' with zero frames over '{}', which holds {} frames — "
            + "the recording on disk and its .dat~N backups were left untouched.",
            filename, file.getName(), existing);

        return false;
    }

    /**
     * Number of frames the recording at {@code file} holds, or {@code -1} when
     * it cannot be read (missing, not gzip, not NBT, no {@code Frames} list).
     *
     * <p>Reads only the {@code Frames} list length, but it has to inflate the
     * whole compound to get there — which is why {@link #acceptSave} calls it
     * only on the empty-in-memory path. Recordings are kilobytes.</p>
     */
    public static int countFrames(File file)
    {
        if (file == null || !file.isFile())
        {
            return -1;
        }

        try (FileInputStream stream = new FileInputStream(file))
        {
            NbtCompound compound = NbtIo.readCompressed(stream);

            if (!compound.contains("Frames", NbtElement.LIST_TYPE))
            {
                return -1;
            }

            return compound.getList("Frames", NbtElement.COMPOUND_TYPE).size();
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    /**
     * This method removes the last file, and renames past versions of a recording files.
     * This should save countless hours of work in case somebody accidentally overwrote
     * a player recording.
     */
    private void savePastCopies(File file)
    {
        /* P284: the algorithm moved verbatim into PastCopies so scenes (which
         * had no backup chain at all) can reuse it rather than grow a second
         * copy that drifts. Behaviour is byte-for-byte what it was. */
        PastCopies.rotate(file, ".dat");
    }

    /**
     * Creates an action map between action name and an action type byte values
     * for compatibility
     */
    private NbtCompound createActionMap()
    {
        NbtCompound tag = new NbtCompound();

        for (Map.Entry<String, Byte> entry : ActionRegistry.NAME_TO_ID.entrySet())
        {
            tag.putString(entry.getValue().toString(), entry.getKey());
        }

        return tag;
    }

    /**
     * Read a recording from given file.
     */
    public void load(File file) throws IOException
    {
        this.load(NbtIo.readCompressed(new FileInputStream(file)));
    }

    public void load(NbtCompound compound)
    {
        NbtCompound map = null;

        this.version = compound.getShort("Version");
        this.preDelay = compound.getInt("PreDelay");
        this.postDelay = compound.getInt("PostDelay");

        if (compound.contains("Actions", NbtElement.COMPOUND_TYPE))
        {
            map = compound.getCompound("Actions");
        }

        if (compound.contains("PlayerData", NbtElement.COMPOUND_TYPE))
        {
            this.playerData = compound.getCompound("PlayerData");
        }

        /* Total reader (roadmap P211): a missing or wrong-typed "Frames" tag
         * must not crash the loader. Legacy did an unchecked cast of
         * compound.getTag("Frames") that NPEs on absent/garbage input (it only
         * stayed total because every disk caller wrapped it in try/catch); the
         * port makes the reader itself total by reading through the type-checked
         * NbtCompound.getList, which yields an empty list for absent/mismatched
         * tags. The placeholder is an empty (frameless) record. */
        if (!compound.contains("Frames", NbtElement.LIST_TYPE))
        {
            Blockbuster.LOGGER.warn("Record '{}' has no readable Frames list (missing or wrong tag type); loading an empty placeholder record", this.filename);
        }

        NbtList frames = compound.getList("Frames", NbtElement.COMPOUND_TYPE);

        for (int i = 0, c = frames.size(); i < c; i++)
        {
            NbtCompound frameTag = frames.getCompound(i);
            NbtElement actionTag = frameTag.get("Action");
            Frame frame = new Frame();

            frame.fromNBT(frameTag);

            if (actionTag != null)
            {
                try
                {
                    List<Action> actions = new ArrayList<Action>();

                    if (actionTag instanceof NbtCompound)
                    {
                        /* Very old format: single action compound */
                        Action action = this.actionFromNBT((NbtCompound) actionTag, map);

                        if (action != null)
                        {
                            actions.add(action);
                        }
                    }
                    else if (actionTag instanceof NbtList)
                    {
                        NbtList list = (NbtList) actionTag;

                        for (int ii = 0, cc = list.size(); ii < cc; ii++)
                        {
                            Action action = this.actionFromNBT(list.getCompound(ii), map);

                            if (action != null)
                            {
                                actions.add(action);
                            }
                        }
                    }

                    this.actions.add(actions);
                }
                catch (Exception e)
                {
                    System.out.println("Failed to load an action at frame " + i);
                    e.printStackTrace();
                }
            }
            else
            {
                this.actions.add(null);
            }

            this.frames.add(frame);
        }
    }

    private Action actionFromNBT(NbtCompound tag, @Nullable NbtCompound map) throws Exception
    {
        byte type = tag.getByte("Type");
        Action action = null;

        if (map == null)
        {
            action = ActionRegistry.fromType(type);
        }
        else
        {
            String name = map.getString(String.valueOf(type));

            if (ActionRegistry.NAME_TO_CLASS.containsKey(name))
            {
                action = ActionRegistry.fromName(name);
            }
        }

        if (action != null)
        {
            action.fromNBT(tag);
        }

        return action;
    }

    public void reverse()
    {
        Collections.reverse(this.frames);
        Collections.reverse(this.actions);
    }

    public void fillMissingActions()
    {
        while (this.actions.size() < this.frames.size())
        {
            this.actions.add(null);
        }
    }

    public static class FoundAction
    {
        public int tick;
        public MorphAction action;

        public void set(int tick, MorphAction action)
        {
            this.tick = tick;
            this.action = action;
        }
    }

    public static enum MorphType
    {
        REGULAR, PAUSE, FORCE
    }
}
