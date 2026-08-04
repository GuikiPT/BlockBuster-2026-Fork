package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueBoolean.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 */
public class ValueBoolean extends GenericValue<Boolean> implements IServerValue
{
    public ValueBoolean(String id)
    {
        super(id, false);
    }

    public ValueBoolean(String id, boolean defaultValue)
    {
        super(id, defaultValue);
    }

    @Override
    protected Boolean getNullValue()
    {
        return false;
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsBoolean());
    }

    @Override
    public JsonElement valueToJSON()
    {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof AbstractNbtNumber)
        {
            if (((AbstractNbtNumber) tag).intValue() == 1)
            {
                this.set(true);
            }
            else if (((AbstractNbtNumber) tag).intValue() == 0)
            {
                this.set(false);
            }
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtInt.of(this.value ? 1 : 0);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        if (value.equals("1"))
        {
            this.set(true);
        }
        else if (value.equals("0"))
        {
            this.set(false);
        }
        else
        {
            this.set(Boolean.parseBoolean(value));
        }

        return true;
    }

    @Override
    public void copy(Value value)
    {
        superCopy(value);

        if (value instanceof ValueBoolean)
        {
            this.value = ((ValueBoolean) value).value;
        }
    }

    @Override
    public void copyServer(Value value)
    {
        super.copyServer(value);

        if (value instanceof ValueBoolean)
        {
            this.serverValue = ((ValueBoolean) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.value = buffer.readBoolean();
        this.defaultValue = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeBoolean(this.value);
        buffer.writeBoolean(this.defaultValue);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.value = buffer.readBoolean();
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeBoolean(this.value);
    }

    @Override
    public String toString()
    {
        return Boolean.toString(this.value);
    }

    @Override
    public ValueBoolean copy()
    {
        ValueBoolean clone = new ValueBoolean(this.id);
        clone.defaultValue = this.defaultValue;
        clone.value = this.value;
        clone.serverValue = this.serverValue;

        return clone;
    }

    public Boolean interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof Boolean)) return this.value;

        return factor == 1F ? (Boolean) to.value : this.value;
    }
}
