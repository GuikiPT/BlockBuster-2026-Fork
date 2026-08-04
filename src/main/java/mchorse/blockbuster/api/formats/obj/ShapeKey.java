package mchorse.blockbuster.api.formats.obj;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Objects;

/**
 * Shape key (morph-target) entry.
 *
 * NBT keys {@code Name}/{@code Value}/{@code Relative} are a wire/disk contract
 * (see {@link mchorse.blockbuster.api.ModelPose}).
 */
public class ShapeKey
{
    public String name;
    public float value;
    public boolean relative = true;

    public ShapeKey()
    {}

    public ShapeKey(String name, float value)
    {
        this.name = name;
        this.value = value;
    }

    public ShapeKey(String name, float value, boolean relative)
    {
        this(name, value);
        this.relative = relative;
    }

    public ShapeKey setValue(float value)
    {
        this.value = value;

        return this;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ShapeKey)
        {
            ShapeKey shape = (ShapeKey) obj;

            return this.value == shape.value && Objects.equals(this.name, shape.name) && this.relative == shape.relative;
        }

        return super.equals(obj);
    }

    public ShapeKey copy()
    {
        return new ShapeKey(this.name, this.value, this.relative);
    }

    public NbtElement toNBT()
    {
        NbtCompound tag = new NbtCompound();

        tag.putString("Name", this.name);
        tag.putFloat("Value", this.value);
        tag.putBoolean("Relative", this.relative);

        return tag;
    }

    public void fromNBT(NbtCompound key)
    {
        this.name = key.getString("Name");
        this.value = key.getFloat("Value");
        this.relative = key.getBoolean("Relative");
    }
}
