package mchorse.metamorph.bodypart;

import java.util.ArrayList;
import java.util.List;

import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Body part manager (roadmap P51).
 *
 * <p>Merge/pause pair parts <b>by index</b> (not limb), and {@code merge}
 * truncates excess via {@code ensureCount}; {@code afterMerge} falls back to a
 * full copy on size mismatch, else forwards morph-by-morph. {@code toNBT}
 * drops parts serializing to an empty compound — a default part vanishes on
 * save (legacy behavior).</p>
 *
 * <p>Port note: {@code initBodyParts}/{@code reinitBodyParts} were
 * {@code @SideOnly(CLIENT)} in Metamorph because the per-part work builds a
 * client dummy entity. The {@code initiated} latch they guard is plain state, so
 * the methods stay here and the client-only body goes through
 * {@link #initializer}, filled by {@code BodyPartRenderer.install()} at client
 * init (same idiom as {@code AbstractMorph.IRenderDispatcher} /
 * {@code MorphRenderer.provider}). With no initializer installed — dedicated
 * server, headless tests — the latch still flips, exactly as if the legacy
 * client-only method had never been reachable.</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/src/main/java/mchorse/metamorph/bodypart/BodyPartManager.java
 */
public class BodyPartManager
{
    /**
     * Client-side per-part initialization ({@code BodyPart.init}: build the
     * dummy host entity and push the item slots onto it).
     */
    public interface IBodyPartInitializer
    {
        void init(BodyPart part);
    }

    /** Filled by the client; null on a dedicated server. */
    public static IBodyPartInitializer initializer;

    /**
     * List of body parts
     */
    public List<BodyPart> parts = new ArrayList<BodyPart>();

    /**
     * Whether body parts were initiated
     */
    private boolean initiated;

    /**
     * Reset initiated state
     */
    public void reset()
    {
        this.initiated = false;
    }

    /**
     * Initialize every part's client render state, once. The latch is what makes
     * this cheap enough to call from the render path every frame, which is where
     * legacy called it from ({@code CustomMorph.render}/{@code renderOnScreen}).
     */
    public void initBodyParts()
    {
        if (!this.initiated)
        {
            IBodyPartInitializer init = initializer;

            if (init != null)
            {
                for (BodyPart part : this.parts)
                {
                    init.init(part);
                }
            }

            this.initiated = true;
        }
    }

    /**
     * Force a re-init — used when the part list changed underneath the latch
     * (morph editor {@code startEdit}).
     */
    public void reinitBodyParts()
    {
        this.reset();
        this.initBodyParts();
    }

    /**
     * Update body limbs
     */
    public void updateBodyLimbs(AbstractMorph parent, LivingEntity target)
    {
        for (BodyPart part : this.parts)
        {
            part.update(parent, target);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof BodyPartManager)
        {
            BodyPartManager manager = (BodyPartManager) obj;

            return this.parts.equals(manager.parts);
        }

        return super.equals(obj);
    }

    public void copy(BodyPartManager manager)
    {
        this.reset();
        this.parts.clear();

        for (BodyPart part : manager.parts)
        {
            this.parts.add(part.copy());
        }
    }

    public void merge(BodyPartManager manager)
    {
        this.initiated = false;

        for (int i = 0, c = manager.parts.size(); i < c; i++)
        {
            BodyPart part = i < this.parts.size() ? this.parts.get(i) : null;
            BodyPart other = manager.parts.get(i);

            if (part == null)
            {
                this.parts.add(other);
            }
            else if (!part.canMerge(other))
            {
                this.parts.set(i, other.copy());
            }
        }

        this.ensureCount(manager.parts.size());
    }

    public void afterMerge(BodyPartManager manager)
    {
        if (manager.parts.size() != this.parts.size())
        {
            this.copy(manager);

            return;
        }

        for (int i = 0, c = this.parts.size(); i < c; i++)
        {
            BodyPart part = this.parts.get(i);
            BodyPart other = manager.parts.get(i);

            if (part.morph.isEmpty() || other.morph.isEmpty())
            {
                continue;
            }

            part.morph.get().afterMerge(other.morph.get());
        }
    }

    public void pause(AbstractMorph previous, int offset)
    {
        BodyPartManager parts = previous instanceof IBodyPartProvider ? ((IBodyPartProvider) previous).getBodyPart() : null;

        for (int i = 0; i < this.parts.size(); i++)
        {
            BodyPart current = this.parts.get(i);
            BodyPart past = null;

            if (parts != null)
            {
                past = i < parts.parts.size() ? parts.parts.get(i) : null;
            }

            current.pause(past, offset);
        }
    }

    private void ensureCount(int count)
    {
        count = Math.max(count, 0);

        while (this.parts.size() > count)
        {
            this.parts.remove(this.parts.size() - 1);
        }
    }

    /* NBT */

    public NbtList toNBT()
    {
        if (!this.parts.isEmpty())
        {
            NbtList bodyParts = new NbtList();

            for (BodyPart part : this.parts)
            {
                NbtCompound bodyPart = new NbtCompound();

                part.toNBT(bodyPart);

                if (!bodyPart.isEmpty())
                {
                    bodyParts.add(bodyPart);
                }
            }

            return bodyParts;
        }

        return null;
    }

    public void fromNBT(NbtList bodyParts)
    {
        this.parts.clear();

        for (int i = 0, c = bodyParts.size(); i < c; i++)
        {
            NbtCompound bodyPart = bodyParts.getCompound(i);
            BodyPart part = new BodyPart();

            part.fromNBT(bodyPart);
            this.parts.add(part);
        }
    }
}
