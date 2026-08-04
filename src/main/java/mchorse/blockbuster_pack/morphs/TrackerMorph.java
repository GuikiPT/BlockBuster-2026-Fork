package mchorse.blockbuster_pack.morphs;

import com.google.common.base.Objects;

import mchorse.blockbuster_pack.trackers.BaseTracker;
import mchorse.blockbuster_pack.trackers.MorphTracker;
import mchorse.blockbuster_pack.trackers.TrackerRegistry;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * Tracker morph (port of Blockbuster 2.7.2's {@code TrackerMorph}, roadmap
 * P166). A zero-size, invisible-in-world helper morph: it draws a rainbow axis
 * pointer + nameplate (unless {@link #hidden} / F1), but its real job is to run
 * its {@link #tracker}'s {@code track()} every render so Aperture's camera can
 * follow it and video capture can lock onto its label.
 *
 * <p>NBT: a {@code Tracker} compound holding the registry {@code Id} plus the
 * tracker's own fields, and a {@code Hidden} byte (only when true). Unknown ids
 * silently keep the default {@link MorphTracker}; the legacy {@code "minema"}
 * id is remapped to {@link MorphTracker} ("backwards compatibility with old
 * minema tracker that got merge to MorphTracker class").</p>
 *
 * <p><b>Render (batch-4 integration).</b> The in-world rainbow pointer +
 * matrix-extracted nameplate and the 2D {@code renderOnScreen} preview are
 * GL/{@code MatrixStack}-bound and land in the S6 client render pipeline (they
 * also install {@code ApertureCamera.captureHook}). What lives here is the
 * load-bearing non-visual contract: {@link #render} still invokes
 * {@code tracker.track()} every frame (even when hidden — hiding is visual
 * only), so the tracking plumbing works headlessly.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/morphs/TrackerMorph.java
 */
public class TrackerMorph extends AbstractMorph
{
    public BaseTracker tracker = new MorphTracker();

    public boolean hidden = false;

    /** Drives the pointer's rainbow hue on the client; ticked in {@link #update}. */
    public int renderTimer = 0;

    public TrackerMorph()
    {
        this.name = "tracker";
    }

    @Override
    protected String getSubclassDisplayName()
    {
        if (this.tracker != null && !this.tracker.name.isEmpty())
        {
            return IKey.lang("blockbuster.gui.tracker_morph.type." + TrackerRegistry.CLASS_TO_ID.get(this.tracker.getClass())).get() + " (" + this.tracker.name + ")";
        }
        else
        {
            return IKey.lang("blockbuster.gui.tracker_morph.name").get();
        }
    }

    @Override
    public void update(LivingEntity target)
    {
        super.update(target);

        if (target.getWorld().isClient())
        {
            this.renderTimer++;
        }
    }

    /* Render -----------------------------------------------------------------
     *
     * Legacy's @SideOnly(CLIENT) render/renderOnScreen (the rainbow pointer, the
     * origin dot, the nameplate and the 2D preview) live in the client source
     * set as mchorse.blockbuster_pack.client.render.TrackerMorphRenderer,
     * dispatched by morph class through AbstractMorph.renderDispatcher (P54).
     *
     * The load-bearing non-visual half is the tracker's track() call, which
     * legacy made from inside render() — every frame, hidden or not, because
     * Aperture reads the tracked position at the frame's own interpolation. It
     * therefore lives in the renderer too, outside its visibility gate. */

    @Override
    public AbstractMorph create()
    {
        return new TrackerMorph();
    }

    @Override
    public float getWidth(LivingEntity target)
    {
        return 0;
    }

    @Override
    public float getHeight(LivingEntity target)
    {
        return 0;
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof TrackerMorph)
        {
            TrackerMorph morph = (TrackerMorph) from;

            this.tracker = null;

            if (morph.tracker != null)
            {
                this.tracker = morph.tracker.copy();
            }

            this.hidden = morph.hidden;
        }
    }

    @Override
    public boolean canMerge(AbstractMorph morph)
    {
        if (morph instanceof TrackerMorph)
        {
            this.mergeBasic(morph);

            TrackerMorph trackerMorph = (TrackerMorph) morph;

            this.hidden = trackerMorph.hidden;

            if (this.tracker != null)
            {
                return this.tracker.canMerge(trackerMorph.tracker);
            }
        }

        return super.canMerge(morph);
    }

    @Override
    public boolean equals(Object object)
    {
        boolean result = super.equals(object);

        if (object instanceof TrackerMorph)
        {
            TrackerMorph morph = (TrackerMorph) object;

            result = result && Objects.equal(this.tracker, morph.tracker);
            result = result && this.hidden == morph.hidden;

            return result;
        }

        return result;
    }

    @Override
    public boolean useTargetDefault()
    {
        return true;
    }

    @Override
    public void reset()
    {
        /* Legacy quirk (load-bearing): TrackerMorph.reset() does NOT call
         * super.reset() — it leaves settings/hitbox untouched, resetting only
         * the tracker + hidden flag. Kept verbatim. */
        this.tracker = new MorphTracker();
        this.hidden = false;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        if (tag.contains("Tracker", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound tracker = tag.getCompound("Tracker");

            Class<? extends BaseTracker> clazz = TrackerRegistry.ID_TO_CLASS.get(tracker.getString("Id"));

            /* backwards compatibility with old minema tracker that got merge to MorphTracker class */
            if (tracker.getString("Id").equals("minema"))
            {
                clazz = MorphTracker.class;
            }

            if (clazz != null)
            {
                try
                {
                    this.tracker = clazz.getDeclaredConstructor().newInstance();
                    this.tracker.fromNBT(tracker);
                }
                catch (ReflectiveOperationException e)
                {
                    e.printStackTrace();
                }
            }
        }

        if (tag.contains("Hidden", NbtElement.BYTE_TYPE))
        {
            this.hidden = tag.getBoolean("Hidden");
        }
    }

    @Override
    public void toNBT(NbtCompound tag)
    {
        super.toNBT(tag);

        if (this.tracker != null)
        {
            NbtCompound tracker = new NbtCompound();

            tracker.putString("Id", TrackerRegistry.CLASS_TO_ID.get(this.tracker.getClass()));
            this.tracker.toNBT(tracker);
            tag.put("Tracker", tracker);
        }

        if (this.hidden)
        {
            tag.putBoolean("Hidden", this.hidden);
        }
    }
}
