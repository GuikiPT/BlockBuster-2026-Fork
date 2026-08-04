package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtLong;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueLong.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 */
public class ValueLong extends GenericNumberValue<Long> implements IServerValue
{
    public ValueLong(String id)
    {
        super(id, 0L, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public ValueLong(String id, long defaultValue)
    {
        super(id, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public ValueLong(String id, long defaultValue, long min, long max)
    {
        super(id, defaultValue, min, max);
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    protected Long getNullValue()
    {
        return 0L;
    }

    public boolean isInteger()
    {
        return true;
    }

    @Override
    protected Long numberToValue(Number number)
    {
        return number.longValue();
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsLong());
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof AbstractNbtNumber)
        {
            this.set(((AbstractNbtNumber) tag).longValue());
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtLong.of(this.value);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        try
        {
            this.set(Long.parseLong(value));

            return true;
        }
        catch (Exception e)
        {}

        return false;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueLong)
        {
            this.set(((ValueLong) value).value);
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueLong)
        {
            this.serverValue = ((ValueLong) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.defaultValue = buffer.readLong();
        this.min = buffer.readLong();
        this.max = buffer.readLong();
        this.valueFromBytes(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeLong(this.defaultValue);
        buffer.writeLong(this.min);
        buffer.writeLong(this.max);
        this.valueToBytes(buffer);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.set(buffer.readLong());
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeLong(this.value);
    }

    @Override
    public ValueLong copy()
    {
        ValueLong clone = new ValueLong(this.id, this.defaultValue, this.min, this.max);
        clone.value = this.value;

        return clone;
    }

    @Override
    public String toString()
    {
        return Long.toString(this.value);
    }

    public Long interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof Long)) return this.value;

        return (long) interpolation.interpolate(this.value, (Long) to.value, factor);
    }
}
