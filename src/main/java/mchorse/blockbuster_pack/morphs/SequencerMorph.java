package mchorse.blockbuster_pack.morphs;

import mchorse.mclib.utils.MathUtils;
import mchorse.mclib.utils.NBTUtils;
import mchorse.metamorph.api.Morph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.MorphUtils;
import mchorse.metamorph.api.models.IMorphProvider;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.api.morphs.utils.Animation;
import mchorse.metamorph.api.morphs.utils.IAnimationProvider;
import mchorse.metamorph.api.morphs.utils.IMorphGenerator;
import mchorse.metamorph.api.morphs.utils.ISyncableMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Sequencer morph (roadmap P160).
 *
 * <p>Next big thing since S&amp;B — an animated morph that cycles through a
 * list of {@link SequenceEntry sequence entries} with variable per-entry
 * delays. This is a direct verbatim port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster_pack.morphs.SequencerMorph}: the playback automaton
 * ({@link #updateMorph}, {@link #getMorphAt}, {@link #getTickAt}), the seeded
 * randomness formulas ({@link #getRandomSeed}/{@link #getRandomIndex}), the
 * {@code setDuration} animation-stretching {@code ×10000} precision hack, and
 * the per-loop XYZ offset (pixel units, negated in reverse) are wire/disk sync
 * contracts — any deviation desyncs replays between clients, so the math is kept
 * byte-for-byte.</p>
 *
 * <p><b>Load-bearing quirks (do not "fix"):</b></p>
 * <ul>
 *   <li>{@code Offset} NBT tag is written <b>unconditionally</b> (the only such
 *       field in the morph pack) — byte-parity goldens must expect it.</li>
 *   <li>Entry {@code SetDuration}/{@code EndPoint} are written unconditionally
 *       inside each entry.</li>
 *   <li>The {@code ×10000} multiplication in {@link #getMorph()} is an
 *       intentional rounding-defeat; {@link #render}/{@link #genCurrentMorph}
 *       use the un-multiplied variant and push the fraction into partialTicks.</li>
 *   <li>The commented-out {@code updateProgress} line for nested sequencers must
 *       stay a no-op (thanks to MiaoNLI for this update). It is the port's only
 *       Emoticons-motivated logic — roadmap P220 pins it with
 *       {@code SequencerMorphTest#nestedSequencerProgressIsNeverAdvanced}.</li>
 *   <li>{@code current == -1} initial-wrap sets {@code loopCount = 0}
 *       (not {@code ++}) — loop counting differs between the first entry and
 *       subsequent wraps.</li>
 *   <li>{@code getMorphAt} zero-total-duration shortcut returns the <b>last</b>
 *       entry.</li>
 *   <li>{@code ignoreAnimationLengthUI} is a purely-UI flag that still
 *       participates in {@code equals}/{@code copy}/{@code reset}.</li>
 * </ul>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/morphs/SequencerMorph.java
 */
public class SequencerMorph extends AbstractMorph implements IMorphProvider, ISyncableMorph, IMorphGenerator
{
    /**
     * List of sequence entries (morph and their delay)
     */
    public List<SequenceEntry> morphs = new ArrayList<SequenceEntry>();

    /**
     * Current morph
     */
    public Morph currentMorph = new Morph();

    /**
     * Index of current cell
     */
    public int current;

    /**
     * If true, do not render the animation length in the UI of this sequencer morph.
     */
    public boolean ignoreAnimationLengthUI;

    /**
     * Timer on which depends the cycling
     */
    public int timer;

    /**
     * Timer for renderOnScreen (client only)
     */
    public int screenTimer;

    /**
     * Duration of the current
     */
    public float duration;

    /**
     * Duration of the last
     */
    public float lastDuration;

    /**
     * Is current morph enabled set duration
     */
    public boolean morphSetDuration;

    /**
     * Record loop count
     */
    public int loopCount;

    /**
     * Is current morph the first morph of a loop
     */
    public boolean isFirstMorph = false;

    /**
     * Last update tick
     */
    public float lastUpdate;

    /**
     * Reverse playback
     */
    public boolean reverse;

    /**
     * Random order of sequencer playback
     */
    public boolean isRandom;

    /**
     * Whether it's random, and truly random
     */
    public boolean isTrulyRandom;

    /**
     * Times of loop.
     */
    public int loop;

    /**
     * Move the model after each repetition
     */
    public float[] offset = new float[3];

    /**
     * How many times to repeat the offset.
     */
    public int offsetCount;

    /**
     * Keep the progress after merge.
     */
    public boolean keepProgress;

    private Animation animation = new Animation();
    private Random random = new Random();

    public SequencerMorph()
    {
        super();

        this.name = "sequencer";
    }

    @Override
    public void pause(AbstractMorph previous, int offset)
    {
        this.animation.pause(offset);

        FoundMorph found = this.getMorphAt(offset);

        if (found == null)
        {
            return;
        }

        AbstractMorph morph = MorphUtils.copy(found.getCurrentMorph());

        if (found.previous != null)
        {
            AbstractMorph prevMorph = MorphUtils.copy(found.getPreviousMorph());

            MorphUtils.pause(prevMorph, previous, (int) found.getPreviousDuration());
            found.applyPrevious(prevMorph);

            previous = prevMorph;
        }

        MorphUtils.pause(morph, previous, (int) (offset - found.lastDuration));
        MorphUtils.resume(morph);

        found.applyCurrent(morph);
        this.currentMorph.setDirect(morph);

        this.timer = offset;
        this.duration = found.totalDuration;
        this.current = found.index;
        this.loopCount = found.loopCount;
        this.isFirstMorph = found.isFirstMorph;
        this.lastDuration = found.lastDuration;
        this.morphSetDuration = found.current.setDuration;
        this.lastUpdate = offset;
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
        MorphUtils.resume(this.currentMorph.get());
    }

    @Override
    public AbstractMorph getMorph()
    {
        AbstractMorph morph = this.currentMorph.get();
        float progress = this.timer;
        float duration = this.duration - this.lastDuration;

        if (this.morphSetDuration)
        {
            if (duration > 0)
            {
                float setDuration = (float) Math.ceil(duration);
                float ticks = (progress - this.lastDuration) * setDuration / duration;
                int tick = (int) ticks * 10000;

                this.updateSetDuration(morph, tick, (int) setDuration * 10000);
            }
            else
            {
                this.updateSetDuration(morph, 1, 1);
            }
        }

        return morph;
    }

    @Override
    public boolean canGenerate()
    {
        AbstractMorph morph = this.currentMorph.get();

        if (morph instanceof IMorphGenerator)
        {
            return ((IMorphGenerator) morph).canGenerate();
        }
        else
        {
            return false;
        }
    }

    @Override
    public AbstractMorph genCurrentMorph(float partialTicks)
    {
        AbstractMorph morph = this.currentMorph.get();

        if (morph instanceof IMorphGenerator)
        {
            float progress = this.timer + partialTicks;
            float duration = this.duration - this.lastDuration;

            if (this.morphSetDuration && duration > 0)
            {
                float setDuration = (float) Math.ceil(duration);
                float ticks = (progress - this.lastDuration) * setDuration / duration;
                int tick = (int) ticks;

                if (this.updateSetDuration(morph, tick, (int) setDuration))
                {
                    partialTicks = ticks - tick;
                }
            }

            return ((IMorphGenerator) morph).genCurrentMorph(partialTicks);
        }
        else
        {
            return null;
        }
    }

    @Override
    protected String getSubclassDisplayName()
    {
        /* SEAM(P160): legacy returned I18n.format("blockbuster.morph.sequencer");
         * localized display name is a client concern — kept as the raw name in
         * the headless main source set (the P54 client renderer/GUI localizes). */
        return this.name;
    }

    /* Render -----------------------------------------------------------------
     *
     * The two render bodies (legacy's @SideOnly(CLIENT) render/renderOnScreen)
     * live in the client source set as
     * mchorse.blockbuster_pack.client.render.SequencerMorphRenderer, dispatched
     * by morph class through AbstractMorph.renderDispatcher (P54). They drive
     * the automaton at render rate, which is why updateClient/updateSetDuration
     * below are public rather than legacy's protected — the body that called
     * them used to be a member of this class.
     *
     * renderHand stays here: it is pure delegation to the current entry, with no
     * client-only type in its signature. */

    @Override
    public boolean renderHand(PlayerEntity player, Hand hand)
    {
        AbstractMorph morph = this.currentMorph.get();

        if (morph != null)
        {
            return morph.renderHand(player, hand);
        }

        return false;
    }

    /* Random stuff */

    private Random getRandomSeed(float duration)
    {
        if (this.isTrulyRandom)
        {
            this.random.setSeed(System.nanoTime());
        }
        else
        {
            this.random.setSeed((long) (duration * 100000L));
        }

        return this.random;
    }

    public AbstractMorph getRandom()
    {
        if (this.morphs.isEmpty())
        {
            return null;
        }

        double factor = this.isTrulyRandom ? Math.random() : this.random.nextDouble();

        return this.get((int) (factor * this.morphs.size()));
    }

    public int getRandomIndex(float duration)
    {
        return (int) (this.getRandomSeed(duration * 2 + 5).nextFloat() * this.morphs.size());
    }

    public AbstractMorph get(int index)
    {
        if (index >= this.morphs.size() || index < 0)
        {
            return null;
        }

        return this.morphs.get(index).morph;
    }

    /* Search */

    public FoundMorph getMorphAt(int tick)
    {
        /* There is no found morph if there are no sequences or tick is negative */
        if (this.morphs.isEmpty() || tick < 0)
        {
            return null;
        }

        float duration = this.getMaxDuration();
        int size = this.morphs.size();

        /* A shortcut in case the durations of every sequence is zero */
        if (duration <= 0)
        {
            return new FoundMorph(size - 1, this.morphs.get(size - 1), size == 1 ? null : this.morphs.get(size - 2), 0, 0, 0, 0, false);
        }

        /* Now the main fun part */
        SequenceEntry entry = null;
        SequenceEntry lastEntry = null;
        int i = this.reverse ? size - 1 : 0;
        int lastIndex = i;

        duration = 0;

        if (this.isRandom)
        {
            i = this.getRandomIndex(duration);
        }

        float lastDuration = 0;
        float prevLastDuration = 0;

        int loopCount = 0;
        int lastLoopCount = 0;
        boolean isFirstMorph = false;
        boolean lastIsFirstMorph = false;

        do
        {
            prevLastDuration = lastDuration;
            lastDuration = duration;
            lastEntry = entry;
            lastLoopCount = loopCount;
            lastIsFirstMorph = isFirstMorph;

            entry = this.morphs.get(i);
            lastIndex = i;

            if (entry != null && entry.endPoint && this.loop > 0 && loopCount >= this.loop - 1)
            {
                break;
            }

            isFirstMorph = false;

            if (this.isRandom)
            {
                if (entry != null && entry.endPoint)
                {
                    loopCount++;
                    isFirstMorph = true;
                }

                i = this.getRandomIndex(duration);
            }
            else
            {
                int next = i + (this.reverse ? -1 : 1);
                int current = MathUtils.cycler(next, 0, size - 1);

                if (current != next)
                {
                    if (this.loop > 0 && loopCount >= this.loop - 1)
                    {
                        break;
                    }
                    else
                    {
                        loopCount++;
                        isFirstMorph = true;
                    }
                }

                i = current;
            }

            duration += entry.getDuration(this.getRandomSeed(duration));
        }
        while (duration < tick);

        return entry == null ? null : new FoundMorph(lastIndex, entry, lastEntry, duration, lastDuration, prevLastDuration, lastLoopCount, lastIsFirstMorph);
    }

    public int getTickAt(int index)
    {
        if (this.morphs.isEmpty() || index < 0 || this.getMaxDuration() < 0.0001F || this.isRandom && this.isTrulyRandom)
        {
            return (int) this.getDuration();
        }

        int size = this.morphs.size();

        int i = -1;
        float duration = 0;

        int loopCount = 0;

        while (i != index)
        {
            SequenceEntry entry = null;

            if (i > 0 && i < size)
            {
                entry = this.morphs.get(i);
            }

            if (entry != null && entry.endPoint && this.loop > 0 && this.loopCount >= this.loop - 1)
            {
                break;
            }

            if (this.isRandom)
            {
                if (entry != null && entry.endPoint)
                {
                    loopCount++;
                }

                i = this.getRandomIndex(duration);
            }
            else
            {
                int next = i + (this.reverse ? -1 : 1);
                int current = MathUtils.cycler(next, 0, size - 1);

                if (current != next)
                {
                    if (this.loop > 0 && loopCount >= this.loop - 1)
                    {
                        break;
                    }
                    else
                    {
                        if (i == -1)
                        {
                            this.loopCount = 0;
                        }
                        else
                        {
                            this.loopCount++;
                        }
                    }
                }

                i = current;
            }

            duration += this.morphs.get(i).getDuration(this.getRandomSeed(duration));
        }

        return (int) duration;
    }

    /**
     * Get duration of this sequencer morph for a single cycle
     */
    public float getDuration()
    {
        float duration = 0F;

        for (SequenceEntry entry : this.morphs)
        {
            duration += entry.getDuration(this.getRandomSeed(duration));
        }

        return duration;
    }

    /**
     * Get maximum duration
     */
    public float getMaxDuration()
    {
        float duration = 0F;

        for (SequenceEntry entry : this.morphs)
        {
            duration += entry.duration + Math.max(entry.random, 0);
        }

        return duration;
    }

    @Override
    public void update(LivingEntity target)
    {
        this.updateCycle();

        AbstractMorph morph = this.currentMorph.get();

        if (morph != null)
        {
            morph.update(target);
        }
    }

    /**
     * Update timer and morph for render (client only)
     */
    public void updateClient(LivingEntity entity, float progress)
    {
        this.updateMorph(progress);
    }

    /**
     * Update the cycle timer
     */
    protected void updateCycle()
    {
        if (this.isPaused())
        {
            return;
        }

        this.updateMorph(this.timer);
        this.timer++;
    }

    /**
     * Update the current morph, make sure that we have currently the
     * correct morph.
     */
    protected void updateMorph(float timer)
    {
        while (!this.morphs.isEmpty() && timer >= this.duration)
        {
            int size = this.morphs.size();
            SequenceEntry entry = null;

            if (this.current >= 0 && this.current < size)
            {
                entry = this.morphs.get(this.current);
            }

            if (entry != null && entry.endPoint && this.loop > 0 && this.loopCount >= this.loop - 1)
            {
                break;
            }

            this.isFirstMorph = false;

            if (this.isRandom)
            {
                if (entry != null && entry.endPoint)
                {
                    this.loopCount++;
                    this.isFirstMorph = true;
                }

                this.current = this.getRandomIndex(this.duration);
            }
            else
            {
                int next = this.current + (this.reverse ? -1 : 1);
                int current = MathUtils.cycler(next, 0, size - 1);

                if (current != next)
                {
                    if (this.loop > 0 && this.loopCount >= this.loop - 1)
                    {
                        return;
                    }
                    else
                    {
                        if (this.current == -1)
                        {
                            this.loopCount = 0;
                        }
                        else
                        {
                            this.loopCount++;
                        }

                        this.isFirstMorph = true;
                    }
                }

                this.current = current;
            }

            if (this.current >= 0 && this.current < size)
            {
                entry = this.morphs.get(this.current);
                AbstractMorph morph = MorphUtils.copy(entry.morph);
                float duration = entry.getDuration(this.getRandomSeed(this.duration));

                this.updateProgress(this.currentMorph.get(), (int) (this.duration - this.lastDuration) - (int) Math.max(this.lastUpdate - this.lastDuration, 0));

                if (this.morphSetDuration)
                {
                    this.updateSetDuration(this.currentMorph.get(), 1, 1);
                }

                this.currentMorph.set(morph);
                this.lastDuration = this.duration;
                this.duration += duration;
                this.morphSetDuration = entry.setDuration;
            }

            if (this.duration - this.lastDuration < 0.0001 && this.getMaxDuration() < 0.0001)
            {
                break;
            }
        }
    }

    /**
     * Set animation's duration to 1
     */
    public boolean updateSetDuration(AbstractMorph morph, int progress, int duration)
    {
        boolean result = false;

        if (!(morph instanceof SequencerMorph) && morph instanceof IMorphProvider)
        {
            result |= updateSetDuration(((IMorphProvider) morph).getMorph(), progress, duration);
        }

        if (morph instanceof IAnimationProvider)
        {
            ((IAnimationProvider) morph).getAnimation().duration = duration;
            ((IAnimationProvider) morph).getAnimation().progress = progress;

            result = true;
        }

        if (morph instanceof IBodyPartProvider)
        {
            for (BodyPart part : ((IBodyPartProvider) morph).getBodyPart().parts)
            {
                result |= this.updateSetDuration(part.morph.get(), progress, duration);
            }
        }

        return result;
    }

    protected void updateProgress(AbstractMorph morph, int progress)
    {
        if (morph instanceof SequencerMorph)
        {
            //breaks keep progress with the fix that emoticons action animation flickers (thanks to MiaoNLI for this update)
            //((SequencerMorph) morph).timer += progress;
            /* P220: intentionally empty. The branch is load-bearing — it makes
             * the IMorphProvider recursion below an else, so a nested sequencer
             * is skipped entirely. Pinned by SequencerMorphTest. */
        }
        else if (morph instanceof IMorphProvider)
        {
            updateProgress(((IMorphProvider) morph).getMorph(), progress);
        }

        if (morph instanceof IAnimationProvider)
        {
            ((IAnimationProvider) morph).getAnimation().progress += progress;
        }

        if (morph instanceof IBodyPartProvider)
        {
            for (BodyPart part : ((IBodyPartProvider) morph).getBodyPart().parts)
            {
                this.updateProgress(part.morph.get(), progress);
            }
        }
    }

    @Override
    public AbstractMorph create()
    {
        return new SequencerMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof SequencerMorph)
        {
            SequencerMorph morph = (SequencerMorph) from;

            for (SequenceEntry entry : morph.morphs)
            {
                this.morphs.add(entry.clone());
            }

            this.reverse = morph.reverse;
            this.isRandom = morph.isRandom;
            this.isTrulyRandom = morph.isTrulyRandom;

            /* Runtime properties */
            this.currentMorph.copy(morph.currentMorph);
            this.timer = morph.timer;
            this.current = morph.current;
            this.duration = morph.duration;

            this.loop = morph.loop;
            this.offset[0] = morph.offset[0];
            this.offset[1] = morph.offset[1];
            this.offset[2] = morph.offset[2];
            this.offsetCount = morph.offsetCount;

            this.keepProgress = morph.keepProgress;
            this.ignoreAnimationLengthUI = morph.ignoreAnimationLengthUI;
        }
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        AbstractMorph morph = this.currentMorph.get();

        return morph == null ? 0 : morph.getWidth(target);
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        AbstractMorph morph = this.currentMorph.get();

        return morph == null ? 0 : morph.getHeight(target);
    }

    @Override
    public boolean equals(Object obj)
    {
        boolean result = super.equals(obj);

        if (obj instanceof SequencerMorph)
        {
            SequencerMorph seq = (SequencerMorph) obj;

            result = result && Objects.equals(this.morphs, seq.morphs);
            result = result && this.reverse == seq.reverse;
            result = result && this.isRandom == seq.isRandom;
            result = result && this.isTrulyRandom == seq.isTrulyRandom;
            result = result && this.loop == seq.loop;
            result = result && Objects.deepEquals(this.offset, seq.offset);
            result = result && this.offsetCount == seq.offsetCount;
            result = result && this.keepProgress == seq.keepProgress;
            result = result && this.ignoreAnimationLengthUI == seq.ignoreAnimationLengthUI;
        }

        return result;
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof SequencerMorph)
        {
            SequencerMorph sequencer = (SequencerMorph) morph;

            if (!sequencer.morphs.equals(this.morphs))
            {
                this.mergeBasic(morph);
                this.morphs.clear();

                for (SequenceEntry entry : sequencer.morphs)
                {
                    this.morphs.add(entry.clone());
                }

                this.current = -1;
                this.duration = 0;

                if (!sequencer.keepProgress)
                {
                    this.timer = 0;
                }

                this.reverse = sequencer.reverse;
                this.isRandom = sequencer.isRandom;

                this.loopCount = 0;
                this.isFirstMorph = false;
                this.loop = sequencer.loop;
                this.offset[0] = sequencer.offset[0];
                this.offset[1] = sequencer.offset[1];
                this.offset[2] = sequencer.offset[2];
                this.offsetCount = sequencer.offsetCount;

                this.lastDuration = 0;
                this.lastUpdate = 0;

                return true;
            }
        }

        return super.canMerge(morph);
    }

    @Override
    public void afterMerge(AbstractMorph morph)
    {
        super.afterMerge(morph);

        this.currentMorph.setDirect(morph);
        this.current = -1;
        this.duration = 0;
        this.timer = 0;
        this.loopCount = 0;
        this.isFirstMorph = false;
        this.lastDuration = 0;
        this.lastUpdate = 0;

        if (morph instanceof SequencerMorph)
        {
            SequencerMorph sequencer = (SequencerMorph) morph;

            if (this.keepProgress)
            {
                this.timer = sequencer.timer;
            }
        }
    }

    @Override
    public void reset()
    {
        super.reset();

        this.current = -1;
        this.timer = 0;
        this.duration = 0;
        this.loopCount = 0;
        this.reverse = false;
        this.currentMorph.setDirect(null);
        this.morphs.clear();
        this.loop = 0;
        this.offset[0] = this.offset[1] = this.offset[2] = 0;
        this.offsetCount = 0;
        this.lastUpdate = 0;
        this.keepProgress = false;
        this.ignoreAnimationLengthUI = false;
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.reverse) tag.putBoolean("Reverse", this.reverse);
        if (this.isRandom) tag.putBoolean("Random", this.isRandom);
        if (this.isTrulyRandom) tag.putBoolean("TrulyRandom", this.isTrulyRandom);
        if (this.loop > 0) tag.putInt("Loop", this.loop);
        tag.put("Offset", NBTUtils.writeFloatList(new NbtList(), this.offset));
        if (this.offsetCount > 0) tag.putInt("OffsetCount", this.offsetCount);
        if (this.keepProgress) tag.putBoolean("KeepProgress", this.keepProgress);
        if (this.ignoreAnimationLengthUI) tag.putBoolean("IgnoreAnimationLengthUI", this.ignoreAnimationLengthUI);

        if (!this.morphs.isEmpty())
        {
            NbtList list = new NbtList();

            for (SequenceEntry entry : this.morphs)
            {
                list.add(entry.toNBT());
            }

            tag.put("List", list);
        }
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Reverse")) this.reverse = tag.getBoolean("Reverse");
        if (tag.contains("Random")) this.isRandom = tag.getBoolean("Random");
        if (tag.contains("TrulyRandom")) this.isTrulyRandom = tag.getBoolean("TrulyRandom");
        if (tag.contains("Loop")) this.loop = tag.getInt("Loop");
        if (tag.contains("Offset")) NBTUtils.readFloatList(tag.getList("Offset", NbtElement.FLOAT_TYPE), this.offset);
        if (tag.contains("OffsetCount")) this.offsetCount = tag.getInt("OffsetCount");
        if (tag.contains("KeepProgress")) this.keepProgress = tag.getBoolean("KeepProgress");
        if (tag.contains("IgnoreAnimationLengthUI")) this.ignoreAnimationLengthUI = tag.getBoolean("IgnoreAnimationLengthUI");

        if (tag.contains("List", NbtElement.LIST_TYPE))
        {
            NbtList list = tag.getList("List", NbtElement.COMPOUND_TYPE);

            for (int i = 0, c = list.size(); i < c; i++)
            {
                SequenceEntry entry = new SequenceEntry();

                entry.fromNBT(list.getCompound(i));
                this.morphs.add(entry);
            }

            this.current = -1;
            this.duration = 0;
            this.timer = 0;
            this.loopCount = 0;
            this.lastUpdate = 0;
        }
    }

    /**
     * Sequence entry
     *
     * Represents a data class cell/entry thing that stores morph and
     * its delay/duration until next morph in the sequence.
     */
    public static class SequenceEntry
    {
        public AbstractMorph morph;
        public float duration = 10;
        public float random = 0;
        public boolean setDuration;
        public boolean endPoint;

        public SequenceEntry()
        {}

        public SequenceEntry(AbstractMorph morph)
        {
            this.morph = morph;
        }

        public SequenceEntry(AbstractMorph morph, float duration)
        {
            this(morph, duration, 0);
        }

        public SequenceEntry(AbstractMorph morph, float duration, float random)
        {
            this(morph, duration, random, true);
        }

        public SequenceEntry(AbstractMorph morph, float duration, float random, boolean setDuration)
        {
            this(morph, duration, random, setDuration, false);
        }

        public SequenceEntry(AbstractMorph morph, float duration, float random, boolean setDuration, boolean endPoint)
        {
            this.morph = morph;
            this.duration = duration;
            this.random = random;
            this.setDuration = setDuration;
            this.endPoint = endPoint;
        }

        public float getDuration(Random random)
        {
            return this.duration + (this.random != 0 ? random.nextFloat() * this.random : 0);
        }

        @Override
        public SequenceEntry clone()
        {
            return new SequenceEntry(MorphUtils.copy(this.morph), this.duration, this.random, this.setDuration, this.endPoint);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof SequenceEntry)
            {
                SequenceEntry entry = (SequenceEntry) obj;

                return Objects.equals(this.morph, entry.morph)
                    && this.duration == entry.duration
                    && this.random == entry.random
                    && this.setDuration == entry.setDuration
                    && this.endPoint == entry.endPoint;
            }

            return super.equals(obj);
        }

        public NbtCompound toNBT()
        {
            NbtCompound entryTag = new NbtCompound();

            if (this.morph != null)
            {
                entryTag.put("Morph", this.morph.toNBT());
            }

            entryTag.putFloat("Duration", this.duration);
            entryTag.putFloat("Random", this.random);
            entryTag.putBoolean("SetDuration", this.setDuration);
            entryTag.putBoolean("EndPoint", this.endPoint);

            return entryTag;
        }

        public void fromNBT(NbtCompound tag)
        {
            if (tag.contains("Morph", NbtElement.COMPOUND_TYPE))
            {
                this.morph = MorphManager.INSTANCE.morphFromNBT(tag.getCompound("Morph"));
            }

            if (tag.contains("Duration", 99))
            {
                this.duration = tag.getFloat("Duration");
            }

            if (tag.contains("Random", 99))
            {
                this.random = tag.getFloat("Random");
            }

            this.setDuration = tag.contains("SetDuration") && tag.getBoolean("SetDuration");
            this.endPoint = tag.contains("EndPoint") && tag.getBoolean("EndPoint");
        }
    }

    /**
     * Data class that is responsible for storing found morph(s)
     * when doing search for specific morph at given tick
     */
    public static class FoundMorph
    {
        public int index;
        public SequenceEntry current;
        public SequenceEntry previous;
        public float totalDuration;
        public float lastDuration;
        public float prevLastDuration;
        public int loopCount;
        public boolean isFirstMorph;

        public FoundMorph(int index, SequenceEntry current, SequenceEntry previous, float totalDuration, float lastDuration, float prevLastDuration, int loopCount, boolean isFirstMorph)
        {
            this.index = index;
            this.current = current;
            this.previous = previous;
            this.totalDuration = totalDuration;
            this.lastDuration = lastDuration;
            this.prevLastDuration = prevLastDuration;
            this.loopCount = loopCount;
            this.isFirstMorph = isFirstMorph;
        }

        public AbstractMorph getCurrentMorph()
        {
            return this.current == null ? null : this.current.morph;
        }

        public AbstractMorph getPreviousMorph()
        {
            return this.previous == null ? null : this.previous.morph;
        }

        public float getCurrentDuration()
        {
            return this.totalDuration - this.lastDuration;
        }

        public float getPreviousDuration()
        {
            return this.lastDuration - this.prevLastDuration;
        }

        public void applyCurrent(AbstractMorph morph)
        {
            if (this.current.setDuration && morph instanceof IAnimationProvider)
            {
                ((IAnimationProvider) morph).getAnimation().duration = (int) this.getCurrentDuration();
            }
        }

        public void applyPrevious(AbstractMorph morph)
        {
            if (this.previous.setDuration && morph instanceof IAnimationProvider)
            {
                ((IAnimationProvider) morph).getAnimation().duration = (int) this.getPreviousDuration();
            }
        }
    }
}
