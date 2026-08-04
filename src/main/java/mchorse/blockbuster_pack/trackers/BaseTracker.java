package mchorse.blockbuster_pack.trackers;

import java.util.Objects;

import mchorse.mclib.network.INBTSerializable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

/**
 * Base tracker contract (port of Blockbuster 2.7.2's
 * {@code mchorse.blockbuster_pack.trackers.BaseTracker}, roadmap P166).
 *
 * <p>A tracker is the pluggable behaviour attached to a {@link
 * mchorse.blockbuster_pack.morphs.TrackerMorph}: it carries a {@link #name}
 * (the tracking label), an {@link #init()} hook run from the constructor, a
 * render-time {@link #track} callback and reflective {@link #copy()} semantics.
 * Subclasses are discriminated on disk by the {@link TrackerRegistry} id.</p>
 *
 * <p>Port note: the legacy {@code track} signature took a 1.12.2
 * {@code EntityLivingBase}; yarn's {@code LivingEntity} replaces it. The
 * reflective {@code getClass().newInstance()} becomes {@code
 * getDeclaredConstructor().newInstance()} (Java 17) with the same
 * printStackTrace-on-failure behaviour.</p>
 *
 * Legacy source: blockbuster-1.12/src/main/java/mchorse/blockbuster_pack/trackers/BaseTracker.java
 */
public abstract class BaseTracker implements INBTSerializable
{
    public String name = "";

    public BaseTracker()
    {
        this.init();
    }

    public void init()
    {}

    /**
     * Render-time tracking callback. Runs every render (even when the owning
     * morph is hidden). The default is a no-op.
     */
    public void track(LivingEntity entity, double x, double y, double z, float entityYaw, float partialTicks)
    {}

    public BaseTracker copy()
    {
        BaseTracker tracker = null;

        try
        {
            tracker = this.getClass().getDeclaredConstructor().newInstance();
            tracker.copy(this);
        }
        catch (ReflectiveOperationException e)
        {
            e.printStackTrace();
        }

        return tracker;
    }

    public void copy(BaseTracker tracker)
    {
        if (tracker != null)
        {
            this.name = tracker.name;
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof BaseTracker)
        {
            return Objects.equals(this.name, ((BaseTracker) obj).name);
        }

        return super.equals(obj);
    }

    public boolean canMerge(BaseTracker morph)
    {
        if (morph != null)
        {
            return this.name.equals(morph.name);
        }

        return false;
    }

    @Override
    public void fromNBT(NbtCompound tag)
    {
        this.name = tag.getString("Name");
    }

    @Override
    public NbtCompound toNBT(NbtCompound tag)
    {
        tag.putString("Name", this.name);

        return tag;
    }
}
