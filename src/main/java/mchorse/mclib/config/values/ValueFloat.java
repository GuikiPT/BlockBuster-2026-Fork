package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueFloat.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 */
public class ValueFloat extends GenericNumberValue<Float> implements IServerValue
{
    public ValueFloat(String id)
    {
        super(id, 0F, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    public ValueFloat(String id, float defaultValue)
    {
        super(id, defaultValue, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    public ValueFloat(String id, float defaultValue, float min, float max)
    {
        super(id, defaultValue, min, max);
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    protected Float getNullValue()
    {
        return 0F;
    }

    @Override
    protected Float numberToValue(Number number)
    {
        return number.floatValue();
    }

    public boolean isInteger()
    {
        return false;
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsFloat());
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof AbstractNbtNumber)
        {
            this.set(((AbstractNbtNumber) tag).floatValue());
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtFloat.of(this.value);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        try
        {
            this.set(Float.parseFloat(value));
        }
        catch (Exception e)
        {}

        return false;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueFloat)
        {
            this.set(((ValueFloat) value).value);
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueFloat)
        {
            this.serverValue = ((ValueFloat) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.defaultValue = buffer.readFloat();
        this.min = buffer.readFloat();
        this.max = buffer.readFloat();
        this.valueFromBytes(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeFloat(this.defaultValue);
        buffer.writeFloat(this.min);
        buffer.writeFloat(this.max);
        this.valueToBytes(buffer);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.set(buffer.readFloat());
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeFloat(this.value);
    }

    @Override
    public String toString()
    {
        return Float.toString(this.value);
    }

    @Override
    public ValueFloat copy()
    {
        ValueFloat clone = new ValueFloat(this.id, this.defaultValue, this.min, this.max);
        clone.value = this.value;

        return clone;
    }

    public Float interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof Float)) return this.value;

        return interpolation.interpolate(this.value, (Float) to.value, factor);
    }
}
