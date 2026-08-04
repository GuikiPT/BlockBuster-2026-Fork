package mchorse.blockbuster_pack.morphs;

import mchorse.blockbuster.Blockbuster;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.recording.actions.PacketRequestAction;
import mchorse.blockbuster.recording.RecordPlayer;
import mchorse.blockbuster.recording.actions.Action;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.blockbuster.recording.data.Mode;
import mchorse.blockbuster.recording.data.Record;
import mchorse.blockbuster.recording.scene.Replay;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Record morph (roadmap P161) — a morph that spawns a client-only ghost
 * {@link EntityActor} which plays back a named record, rendering the actor's
 * current morph offset by its interpolated delta from frame 0. Port of 2.7.2
 * {@code mchorse.blockbuster_pack.morphs.RecordMorph} (name
 * {@code "blockbuster.record"}).
 *
 * <p><b>NBT contract (byte-parity with 1.12.2):</b> {@code Initial} (nested
 * morph compound), {@code Record} (string, written only when non-empty),
 * {@code Loop} (written <b>only when false</b>; default true), and {@code
 * RandomDelay} — an int whose <b>key deliberately does not match</b> the field
 * / GUI name {@code randomSkip} (written only when non-zero). These quirks are
 * load-bearing and preserved verbatim.</p>
 *
 * <p><b>Client seams (S22 P235).</b> The ghost actor is client-only, never
 * spawned into the world, and driven straight from the morph's tick. This class
 * lives in the common source set, so the two client-only touchpoints are routed
 * through static seams that
 * {@code mchorse.blockbuster_pack.client.RecordMorphClient.install()} assigns
 * from {@code BlockbusterClient}:</p>
 * <ul>
 *   <li>{@link #recordSource} — the client record cache lookup
 *       ({@code ClientProxy.manager::getClient}); null on the server.</li>
 *   <li>{@link #recordRequester} — the client-side record request RPC (legacy
 *       {@code ServerHandlerRequestRecording.requestRecording}, which the split
 *       source sets moved onto {@code ClientHandlerFramesLoad}, P115/P116).</li>
 * </ul>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/morphs/RecordMorph.java
 */
public class RecordMorph extends AbstractMorph implements ISyncableMorph
{
    /**
     * Client record-cache lookup seam. Assigned by
     * {@code mchorse.blockbuster_pack.client.RecordMorphClient.install()} to
     * {@code mchorse.blockbuster.ClientProxy.manager::getClient} (S22 P235).
     * Null on the server — {@link #initiateActor}/{@link #updateActor} treat
     * null as "record not available yet".
     */
    public static Function<String, Record> recordSource;

    /**
     * Client record-request seam. Assigned by
     * {@code mchorse.blockbuster_pack.client.RecordMorphClient.install()} to
     * {@code ClientHandlerFramesLoad::requestRecording} (S22 P235) — legacy's
     * {@code ServerHandlerRequestRecording.requestRecording}, which asks the
     * server to stream the record's frames back. Null → request is skipped.
     */
    public static Consumer<String> recordRequester;

    /**
     * Ghost actor playing the record (client-only — never added to the world).
     */
    public EntityActor actor;

    public boolean reload;

    /**
     * Initial morph, rendered before playback has begun and re-applied on loop.
     */
    public AbstractMorph initial;

    /**
     * Record which should be played on this morph
     */
    public String record = "";

    /**
     * Loop the actor
     */
    public boolean loop = true;

    /**
     * Random skip factor (NBT key is {@code RandomDelay} — see class doc).
     */
    public int randomSkip;

    private boolean initiate;
    private Animation animation = new Animation();
    private Replay replay = new Replay();

    public RecordMorph()
    {
        super();

        this.name = "blockbuster.record";
    }

    public void setRecord(String record)
    {
        this.record = record;
        this.reload = true;
    }

    /**
     * Pure render-offset helper (headless-testable): the interpolated actor
     * position on one axis, expressed relative to frame 0. Mirrors the legacy
     * {@code lerp(prevPos, pos) - first} render translation.
     */
    public static double renderOffset(double prevPos, double pos, float partialTicks, double first)
    {
        return (prevPos + (pos - prevPos) * partialTicks) - first;
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);
    }

    @Override
    public boolean isPaused()
    {
        return this.animation.paused;
    }

    @Override
    public void resume()
    {
        this.animation.paused = false;
    }

    /**
     * Package-private rather than private purely as the P279 regression seam —
     * a zero-length record must return here before the ghost actor is touched,
     * which is the one thing about this method a headless test can drive (the
     * actor itself needs a live {@code World}).
     */
    void previewActor(Record record)
    {
        int length = record.getLength();

        /* P279: a record with no frames and no actions divides by zero here.
         * That is not hypothetical — every recording saved by a pre-P244 build
         * shipped an empty frame list (client frame capture was dark, so every
         * PacketFramesChunk upload was empty), and this runs in a block-entity
         * ticker, so the throw took the whole client down. Total-reader rule:
         * warn once and render the initial morph instead. */
        if (length <= 0)
        {
            warnEmptyRecord(record.filename);

            return;
        }

        int tick = this.animation.progress % length;

        Frame frame = record.getFrame(Math.max(tick - 1, 0));

        if (frame == null)
        {
            return;
        }

        frame.apply(this.actor, true);

        if (frame.hasBodyYaw)
        {
            this.actor.prevBodyYaw = this.actor.bodyYaw = frame.bodyYaw;
        }

        this.actor.prevX = this.actor.getX();
        this.actor.prevY = this.actor.getY();
        this.actor.prevZ = this.actor.getZ();

        this.actor.prevYaw = this.actor.getYaw();
        this.actor.prevPitch = this.actor.getPitch();
        this.actor.prevHeadYaw = this.actor.getHeadYaw();
        this.actor.prevBodyYaw = this.actor.bodyYaw;
        this.actor.playback.tick = tick;
        this.actor.playback.playing = false;

        if (!this.isPaused())
        {
            frame = record.getFrame(tick);

            if (frame == null)
            {
                return;
            }

            frame.apply(this.actor, true);

            if (frame.hasBodyYaw)
            {
                this.actor.bodyYaw = frame.bodyYaw;
            }
        }

        this.initiate = true;
    }

    @Override
    protected String getSubclassDisplayName()
    {
        return "Record";
    }

    /* Render -----------------------------------------------------------------
     *
     * Legacy's @SideOnly(CLIENT) render/renderOnScreen live in the client source
     * set as mchorse.blockbuster_pack.client.render.RecordMorphRenderer,
     * dispatched by morph class through AbstractMorph.renderDispatcher (P54).
     * They drive the ghost actor from the render call (legacy did too — a record
     * morph that is never drawn never initiates its actor), which is why
     * initiateActor/getActorMorph/initiate below are public rather than private:
     * the body that used to touch them was a member of this class. */

    /**
     * Whether the ghost actor has been seeded to frame 0's position. Public for
     * the client render body only — see the render note above.
     */
    public boolean isInitiated()
    {
        return this.initiate;
    }

    /** Client render body hook: mark the actor seeded (see {@link #isInitiated}). */
    public void setInitiated(boolean initiated)
    {
        this.initiate = initiated;
    }

    /** Read the actor's current morph — legacy {@code this.actor.getMorph()}. */
    public AbstractMorph getActorMorph()
    {
        return this.actor == null ? null : this.actor.getMorph();
    }

    /**
     * Store a morph onto the actor — legacy
     * {@code this.actor.morph.setDirect(...)}. {@code setDirect} rather than
     * {@code set}: the preview actor must land on exactly the instance handed
     * in, with no merge.
     */
    private void setActorMorph(AbstractMorph morph)
    {
        this.actor.morph.setDirect(morph);
    }

    public void initiateActor(World world)
    {
        if (this.reload)
        {
            this.actor = null;
            this.initiate = false;
            this.reload = false;
        }

        if (this.actor == null)
        {
            this.actor = new EntityActor(world);
            this.setActorMorph(MorphUtils.copy(this.initial));
            this.actor.playback = new RecordPlayer(null, Mode.FRAMES, this.actor);
            this.actor.playback.tick = (int) (this.randomSkip * Math.random());
            this.actor.manual = true;

            Record record = getClientRecord(this.record);

            if (record == null && !this.record.isEmpty())
            {
                requestRecording(this.record);
            }
            else if (this.animation.progress != 0 && record != null)
            {
                if (record.actions.isEmpty())
                {
                    /* Just to prevent it from spamming messages */
                    record.actions.add(new ArrayList<Action>());
                    Dispatcher.sendToServer(new PacketRequestAction(this.record, false));
                }

                this.actor.playback.record = record;

                this.replay.morph = this.initial;
                this.previewActor(record);

                this.applyPreviousMorph(record, this.animation.progress, this.isPaused() ? Record.MorphType.PAUSE : Record.MorphType.FORCE);
            }
        }
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        if (target.getWorld().isClient)
        {
            this.updateActor();
        }
    }

    /**
     * Package-private for the P279 regression seam — the {@code actor == null}
     * ("record has not arrived yet") tick is the one branch of this state
     * machine that runs without a live {@code World}.
     */
    void updateActor()
    {
        if (this.actor != null)
        {
            RecordPlayer player = this.actor.playback;

            if (player.record == null)
            {
                player.record = getClientRecord(this.record);

                if (player.record != null)
                {
                    this.previewActor(player.record);
                }

                if (player.record != null && player.record.actions.isEmpty())
                {
                    /* Just to prevent it from spamming messages */
                    player.record.actions.add(new ArrayList<Action>());
                    Dispatcher.sendToServer(new PacketRequestAction(this.record, false));
                }
            }
            else
            {
                if (this.isPaused() && this.actor.playback.record != null)
                {
                    this.previewActor(this.actor.playback.record);
                    this.applyPreviousMorph(this.actor.playback.record, this.animation.progress, Record.MorphType.PAUSE);
                }
                else
                {
                    if (this.animation.progress != 0)
                    {
                        this.actor.playback.playing = true;
                        this.actor.playback.tick = this.animation.progress + 1;

                        this.animation.progress = 0;
                    }

                    /* batch-4 integration: legacy called actor.onUpdate() on the
                     * detached ghost actor to advance the manual playback. The
                     * yarn analog is the full tick; the manual-advance path lives
                     * in EntityActor.tickMovement (manual == true). In-game
                     * verification owns the detached-tick safety. */
                    this.actor.tick();
                }

                if (!this.isPaused() && this.actor.playback.isFinished() && this.loop)
                {
                    this.actor.playback.record.reset(this.actor);
                    this.actor.playback.tick = (int) (this.randomSkip * Math.random());
                    this.actor.playback.record.applyAction(0, this.actor, true);
                    this.setActorMorph(MorphUtils.copy(this.initial));
                }
            }
        }
        else
        {
            this.animation.progress++;
        }
    }

    /**
     * Client record-cache lookup through the {@link #recordSource} seam.
     *
     * <p><b>P279:</b> a cached record with no frames <i>and</i> no actions is
     * reported as "not available yet" rather than handed to the playback state
     * machine. Two reasons, both observed: {@link #previewActor} used to divide
     * by {@code getLength()} and crash the client outright, and binding the
     * empty record to {@code actor.playback.record} latched it forever, so the
     * morph never recovered even after the real frames arrived. Returning null
     * keeps the initial morph on screen and lets {@link #initiateActor}
     * re-request the record from the server — the recovery path for a client
     * that cached a placeholder (e.g. {@code ClientHandlerActions} inserting an
     * actions-only {@code Record} before the frames land).</p>
     */
    static Record getClientRecord(String record)
    {
        Record found = recordSource == null ? null : recordSource.apply(record);

        if (found != null && found.getLength() <= 0)
        {
            warnEmptyRecord(record);

            return null;
        }

        return found;
    }

    /**
     * Names already warned about, so the once-per-record warning does not
     * become a 20 Hz log flood — every call site below runs inside a tick (the
     * crash this guards came out of {@code TileEntityModel}'s block-entity
     * ticker). Keyed by record name, so a record that later loads with frames
     * simply stops warning.
     */
    private static final Set<String> warnedEmpty = ConcurrentHashMap.newKeySet();

    /** Test seam — how many warnings {@link #warnEmptyRecord} actually emitted. */
    private static final AtomicInteger emptyRecordWarnings = new AtomicInteger();

    /** One warning per record name — see {@link #warnedEmpty}. */
    private static void warnEmptyRecord(String record)
    {
        /* String.valueOf: this is a total-reader path — a Record with a null
         * filename must not turn a guarded warning into an NPE. */
        if (warnedEmpty.add(String.valueOf(record)))
        {
            emptyRecordWarnings.incrementAndGet();

            Blockbuster.LOGGER.warn(
                "Record morph: record '{}' has no frames and no actions — rendering the initial morph instead. "
                + "Recordings saved by a build older than S22 P244 were written with an empty frame list; re-record it.",
                record);
        }
    }

    /** Test seam — forget the one-shot warning state. */
    public static void resetEmptyRecordWarnings()
    {
        warnedEmpty.clear();
        emptyRecordWarnings.set(0);
    }

    /** Test seam — the number of warnings emitted since the last reset. */
    public static int emptyRecordWarnings()
    {
        return emptyRecordWarnings.get();
    }

    /**
     * Client record-request through the {@link #recordRequester} seam.
     */
    private static void requestRecording(String record)
    {
        if (recordRequester != null)
        {
            recordRequester.accept(record);
        }
    }

    /**
     * The legacy {@code record.applyPreviousMorph(this.actor, this.replay,
     * progress, type)} scrub call (S22 P235), kept as a one-line wrapper so the
     * two call sites above stay legible and the delegation is assertable
     * headlessly (the ghost actor itself needs a live {@code World}).
     *
     * <p>Package-private, not private, purely for that test. The receiver's own
     * two load-bearing quirks — the early return past the end of the action
     * list, and the swallowed exception that keeps one broken morph from
     * breaking playback — live in {@link Record#applyPreviousMorph} and are not
     * duplicated here.</p>
     */
    void applyPreviousMorph(Record record, int progress, Record.MorphType type)
    {
        record.applyPreviousMorph(this.actor, this.replay, progress, type);
    }

    @Override
    public AbstractMorph create()
    {
        return new RecordMorph();
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof RecordMorph)
        {
            RecordMorph recmorph = (RecordMorph) morph;

            this.mergeBasic(morph);

            if (!recmorph.animation.ignored)
            {
                this.animation.merge(recmorph.animation);
            }

            if (!recmorph.record.equals(this.record))
            {
                this.copy(recmorph);
            }

            return true;
        }

        return super.canMerge(morph);
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof RecordMorph)
        {
            RecordMorph morph = (RecordMorph) from;

            this.record = morph.record;
            this.loop = morph.loop;
            this.randomSkip = morph.randomSkip;
            this.initial = MorphUtils.copy(morph.initial);
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0.6F;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 1.8F;
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof RecordMorph)
        {
            RecordMorph record = (RecordMorph) obj;

            result = result && Objects.equals(record.record, this.record);
            result = result && Objects.equals(record.initial, this.initial);
            result = result && record.loop == this.loop;
            result = result && record.randomSkip == this.randomSkip;
        }

        return result;
    }

    @Override
    public void reset()
    {
        super.reset();

        this.initial = null;
        this.record = "";
        this.reload = true;
        this.loop = true;
        this.randomSkip = 0;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Initial", NbtElement.COMPOUND_TYPE))
        {
            this.initial = MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Initial"));
        }

        if (tag.contains("Record", NbtElement.STRING_TYPE))
        {
            this.record = tag.getString("Record");
        }

        if (tag.contains("Loop"))
        {
            this.loop = tag.getBoolean("Loop");
        }

        if (tag.contains("RandomDelay"))
        {
            this.randomSkip = tag.getInt("RandomDelay");
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.initial != null)
        {
            NbtCompound morph = new NbtCompound();

            this.initial.toNBT(morph);
            tag.put("Initial", morph);
        }

        if (!this.record.isEmpty())
        {
            tag.putString("Record", this.record);
        }

        if (!this.loop)
        {
            tag.putBoolean("Loop", this.loop);
        }

        if (this.randomSkip != 0)
        {
            tag.putInt("RandomDelay", this.randomSkip);
        }
    }
}
