package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.network.ForgeByteBufUtils;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueString.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 */
public class ValueString extends GenericValue<String> implements IServerValue
{
    public ValueString(String id)
    {
        super(id, "");
    }

    public ValueString(String id, String defaultValue)
    {
        super(id, defaultValue);
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsString());
    }

    @Override
    public JsonElement valueToJSON()
    {
        return new JsonPrimitive(this.value);
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof NbtString)
        {
            this.set(((NbtString) tag).asString());
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtString.of(this.value == null ? "" : this.value);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        this.set(value);

        return true;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueString)
        {
            this.value = ((ValueString) value).value;
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueString)
        {
            this.serverValue = ((ValueString) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.value = ForgeByteBufUtils.readUTF8String(buffer);
        this.defaultValue = ForgeByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        ForgeByteBufUtils.writeUTF8String(buffer, this.value == null ? "" : this.value);
        ForgeByteBufUtils.writeUTF8String(buffer, this.defaultValue == null ? "" : this.defaultValue);
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.value = ForgeByteBufUtils.readUTF8String(buffer);
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        ForgeByteBufUtils.writeUTF8String(buffer, this.value == null ? "" : this.value);
    }

    @Override
    public String toString()
    {
        return this.value;
    }

    @Override
    public ValueString copy()
    {
        ValueString clone = new ValueString(this.id);
        clone.defaultValue = this.defaultValue;
        clone.value = this.value;
        clone.serverValue = this.serverValue;

        return clone;
    }

    @Override
    public String interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof String)) return this.value;

        return factor == 1F ? (String) to.value : this.value;
    }
}
