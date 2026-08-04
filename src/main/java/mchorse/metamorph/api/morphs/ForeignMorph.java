package mchorse.metamorph.api.morphs;

import mchorse.mclib.utils.NBTUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Objects;

/**
 * Foreign (unknown-type) morph placeholder — roadmap P220 (Emoticons interop).
 *
 * <p>Metamorph discriminates morphs on the {@code Name} string tag and asks
 * every registered {@link mchorse.metamorph.api.IMorphFactory} in turn. When no
 * factory claims the name — the providing mod is not installed, e.g. an
 * <b>Emoticons</b> morph nested in a sequencer, a body part, a scene replay or a
 * record's {@code MorphAction} — 1.12.2 Metamorph returned {@code null} and the
 * carrier wrote nothing back out. That is silent, permanent data loss on the
 * very first edit-save cycle, and on this platform it is not hypothetical: no
 * Fabric Emoticons exists, so <i>every</i> imported 1.12.2 scene that used one
 * would be stripped by simply opening and saving it.</p>
 *
 * <p>This morph is the "logged warning + placeholder, never a crash" half of the
 * project's total-reader rule made lossless: it keeps the original compound
 * <b>verbatim</b> and re-emits it byte-for-byte from {@link #toNBT(NbtCompound)},
 * so a load → save round trip of a foreign morph is the identity function. It is
 * deliberately inert everywhere else — no renderer is registered for it (so the
 * morph draws exactly like an unmorphed entity, same as legacy's {@code null}),
 * it has default player dimensions and it never merges.</p>
 *
 * <p><b>Deliberate deviation from 1.12.2</b>, documented in
 * {@code plan/S21-optional-compat.md} §P220: legacy dropped the tag. Gameplay
 * semantics are unchanged (an unresolvable morph still renders as nothing and
 * still carries no abilities); only serialization got faithful. If a real
 * Emoticons/foreign factory later registers the same {@code Name}, the preserved
 * tag parses into the real morph on the next load with no migration.</p>
 *
 * @see mchorse.metamorph.api.MorphManager#morphFromNBT(NbtCompound)
 */
public class ForeignMorph extends AbstractMorph
{
    /**
     * The original, unmodified morph compound. Never shared with callers —
     * {@link #getData()} hands out a copy.
     */
    protected NbtCompound data = new NbtCompound();

    /**
     * A copy of the preserved compound (the exact tag this placeholder was
     * created from, after {@code MorphManager}'s in-place {@code Name} remap).
     */
    public NbtCompound getData()
    {
        return NBTUtils.legacyCopy(this.data);
    }

    @Override
    public AbstractMorph create()
    {
        return new ForeignMorph();
    }

    @Override
    public void copy(AbstractMorph from)
    {
        super.copy(from);

        if (from instanceof ForeignMorph)
        {
            this.data = NBTUtils.legacyCopy(((ForeignMorph) from).data);
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
    public boolean canMerge(AbstractMorph morph)
    {
        return false;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ForeignMorph)
        {
            return super.equals(obj) && Objects.equals(this.data, ((ForeignMorph) obj).data);
        }

        return false;
    }

    /**
     * Re-emit the preserved compound verbatim. {@link AbstractMorph#toNBT()} is
     * final and hands us a fresh compound, so writing every key of {@link #data}
     * into it reproduces the source tag exactly — no {@code super} call, because
     * the base writer would inject {@code ForcedSettings} and friends that the
     * foreign mod's own serializer may never have written.
     */
    @Override
    public void toNBT(NbtCompound tag)
    {
        for (String key : this.data.getKeys())
        {
            NbtElement element = this.data.get(key);

            if (element != null)
            {
                /* legacyCopy, not copy(): NbtCompound.copy() pre-sizes the
                 * copy's HashMap from the entry count and so re-buckets the
                 * keys, changing serialised order. This tag is foreign-mod NBT
                 * held verbatim for re-emit — reordering it defeats the point.
                 * S22 P287. */
                tag.put(key, NBTUtils.legacyCopy(element));
            }
        }
    }

    /**
     * Snapshot the tag. {@code super.fromNBT} still runs so {@link #name} and
     * the shared display/hitbox fields are populated for GUIs and
     * {@code equals} — but it can only ever read keys that are already in the
     * snapshot, so it cannot make the round trip lossy.
     */
    @Override
    public void fromNBT(NbtCompound tag)
    {
        super.fromNBT(tag);

        this.data = tag == null ? new NbtCompound() : NBTUtils.legacyCopy(tag);
    }
}
