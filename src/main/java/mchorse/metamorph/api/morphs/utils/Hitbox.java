package mchorse.metamorph.api.morphs.utils;

import net.minecraft.nbt.NbtCompound;

/**
 * Custom morph hitbox (roadmap P47).
 *
 * <p>Quirk preserved byte-for-byte: {@code toNBT} compares
 * {@code Height}/{@code Eye}/{@code Sneak} against {@code 0.6F} (not their
 * real defaults 1.8/0.9/1.65), so those keys are almost always written once
 * the hitbox is touched; {@code Enabled} is written only when true (a disabled
 * hitbox has no {@code Enabled} key, not {@code Enabled:0b}).</p>
 *
 * Legacy source: .tools/legacy-src/metamorph/.../api/morphs/utils/Hitbox.java
 */
public class Hitbox
{
    private static final Hitbox DEFAULT = new Hitbox();

    public boolean enabled;
    public float width;
    public float height;
    public float eye;
    public float sneakingHeight;

    public Hitbox()
    {
        this.reset();
    }

    public void reset()
    {
        this.enabled = false;
        this.width = 0.6F;
        this.height = 1.8F;
        this.eye = 0.9F;
        this.sneakingHeight = 1.65F;
    }

    public void copy(Hitbox hitbox)
    {
        this.enabled = hitbox.enabled;
        this.width = hitbox.width;
        this.height = hitbox.height;
        this.eye = hitbox.eye;
        this.sneakingHeight = hitbox.sneakingHeight;
    }

    public boolean isDefault()
    {
        return this.equals(DEFAULT);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Hitbox)
        {
            Hitbox hitbox = (Hitbox) obj;

            return this.enabled == hitbox.enabled
                && this.width == hitbox.width
                && this.height == hitbox.height
                && this.eye == hitbox.eye
                && this.sneakingHeight == hitbox.sneakingHeight;
        }

        return super.equals(obj);
    }

    public NbtCompound toNBT()
    {
        NbtCompound tag = new NbtCompound();

        if (this.enabled) tag.putBoolean("Enabled", true);
        if (this.width != 0.6F) tag.putFloat("Width", this.width);
        if (this.height != 0.6F) tag.putFloat("Height", this.height);
        if (this.eye != 0.6F) tag.putFloat("Eye", this.eye);
        if (this.sneakingHeight != 0.6F) tag.putFloat("Sneak", this.sneakingHeight);

        return tag;
    }

    public void fromNBT(NbtCompound tag)
    {
        if (tag.contains("Enabled"))
        {
            this.enabled = tag.getBoolean("Enabled");
        }

        if (tag.contains("Width"))
        {
            this.width = tag.getFloat("Width");
        }

        if (tag.contains("Height"))
        {
            this.height = tag.getFloat("Height");
        }

        if (tag.contains("Eye"))
        {
            this.eye = tag.getFloat("Eye");
        }

        if (tag.contains("Sneak"))
        {
            this.sneakingHeight = tag.getFloat("Sneak");
        }
    }
}
