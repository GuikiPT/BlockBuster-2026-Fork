package mchorse.blockbuster.recording;

import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.capturing.PlayerTracker;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Record recorder class (roadmap P109) — 1:1 port of the 2.7.2 class.
 *
 * This thing is responsible for recording a record. It can record actions and
 * frames to the given recorder.
 *
 * Yeah, kinda funky naming, but it is the <s>not</s> best naming, eva!
 */
public class RecordRecorder
{
    /**
     * Initial record
     */
    public Record record;

    /**
     * List of actions which will be saved every time {@link #record(PlayerEntity)}
     * method is invoked.
     */
    public List<Action> actions = new ArrayList<Action>();

    /**
     * Recording mode (record actions, frames or both)
     */
    public Mode mode;

    /**
     * Current recording tick
     */
    public int tick = 0;

    /**
     * Recording offset
     */
    public int offset = 0;

    /**
     * Whether recorded player should be teleported back
     */
    public boolean teleportBack;

    /**
     * First frame (to restore the position)
     */
    private Frame first;

    /**
     * The offset of yaw between before and after {@link MathHelper#wrapDegrees(float)}
     */
    private float yawOffset;

    /**
     * Player tracker (inventory slots, arm swing and container close). Created
     * only for {@link Mode#ACTIONS}/{@link Mode#BOTH} — {@code null} in
     * {@link Mode#FRAMES}, exactly like 1.12.2.
     */
    public PlayerTracker tracker;

    public RecordRecorder(Record record, Mode mode, PlayerEntity player, boolean teleportBack)
    {
        this(record, mode, capture(player), teleportBack);
    }

    /**
     * Test seam so headless tests (which cannot construct a
     * {@code ServerPlayerEntity}) can drive the recorder with synthetic
     * frames. The public {@code (…, PlayerEntity, …)} constructor above is the
     * parity surface; this one skips the player capture only.
     */
    public RecordRecorder(Record record, Mode mode, Frame first, boolean teleportBack)
    {
        this.record = record;
        this.mode = mode;
        this.teleportBack = teleportBack;
        this.first = first;

        this.yawOffset = this.first.yaw - MathHelper.wrapDegrees(this.first.yaw);

        if (mode == Mode.ACTIONS || mode == Mode.BOTH)
        {
            this.tracker = new PlayerTracker(this);
        }
    }

    private static Frame capture(PlayerEntity player)
    {
        Frame frame = new Frame();

        frame.fromPlayer(player);

        return frame;
    }

    /**
     * Record frame from the player
     */
    public void record(PlayerEntity player)
    {
        boolean both = this.mode == Mode.BOTH;
        Frame frame = null;

        if (this.mode == Mode.FRAMES || both)
        {
            frame = capture(player);
        }

        if (this.mode == Mode.ACTIONS || both)
        {
            this.tracker.track(player);
        }

        this.record(frame);
    }

    /**
     * Frame-level half of {@link #record(PlayerEntity)} (headless-test seam);
     * yaw normalization and action flushing match legacy statement order.
     */
    void record(Frame frame)
    {
        boolean both = this.mode == Mode.BOTH;

        if (this.mode == Mode.FRAMES || both)
        {
            frame.yaw -= this.yawOffset;
            frame.yawHead -= this.yawOffset;
            frame.bodyYaw -= this.yawOffset;
            frame.mountYaw -= this.yawOffset;

            this.record.frames.add(frame);
        }

        if (this.mode == Mode.ACTIONS || both)
        {
            List<Action> list = null;

            if (!this.actions.isEmpty())
            {
                list = new ArrayList<Action>();
                list.addAll(this.actions);

                this.actions.clear();
            }

            this.record.actions.add(list);
        }

        this.tick++;
    }

    public void stop(PlayerEntity player)
    {
        if (this.teleportBack && player instanceof ServerPlayerEntity)
        {
            ((ServerPlayerEntity) player).networkHandler.requestTeleport(this.first.x, this.first.y, this.first.z, this.first.yaw, this.first.pitch);
        }
    }

    public void applyOld(Record oldRecord)
    {
        this.record.frames.addAll(oldRecord.frames);

        if (this.offset > 0)
        {
            List<List<Action>> actions = this.record.actions;
            int newSize = this.offset + actions.size();

            this.record.actions = oldRecord.actions;

            if (this.record.actions.size() < newSize)
            {
                while (this.record.actions.size() < newSize)
                {
                    this.record.actions.add(null);
                }
            }

            for (int i = 0; i < actions.size(); i++)
            {
                this.record.addActions(this.offset + i, actions.get(i));
            }
        }
    }
}
