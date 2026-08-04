package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueDouble.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 */
public class ValueDouble extends GenericNumberValue<Double> implements IServerValue
{
    public ValueDouble(String id)
    {
        super(id, 0D, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public ValueDouble(String id, double defaultValue)
    {
        super(id, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    public ValueDouble(String id, double defaultValue, double min, double max)
    {
        super(id, defaultValue, min, max);
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    protected Double getNullValue()
    {
        return 0D;
    }

    @Override
    protected Double numberToValue(Number number)
    {
        return number.doubleValue();
    }

    public boolean isInteger()
    {
        return false;
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsDouble());
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        try
        {
            this.set(Double.parseDouble(value));
        }
        catch (Exception e)
        {}

        return false;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueDouble)
        {
            this.set(((ValueDouble) value).value);
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueDouble)
        {
            this.serverValue = ((ValueDouble) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.defaultValue = buffer.readDouble();
        this.min = buffer.readDouble();
        this.max = buffer.readDouble();
        this.valueFromBytes(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeDouble(this.defaultValue);
        buffer.writeDouble(this.min);
        buffer.writeDouble(this.max);
        this.valueToBytes(buffer);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.set(buffer.readDouble());
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeDouble(this.value);
    }

    @Override
    public String toString()
    {
        return Double.toString(this.value);
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof AbstractNbtNumber)
        {
            this.set(((AbstractNbtNumber) tag).doubleValue());
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtDouble.of(this.value);
    }

    @Override
    public ValueDouble copy()
    {
        ValueDouble clone = new ValueDouble(this.id, this.defaultValue, this.min, this.max);
        clone.value = this.value;

        return clone;
    }

    public Double interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof Double)) return this.value;

        return interpolation.interpolate(this.value, (Double) to.value, factor);
    }
}
