package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.utils.Interpolation;
import mchorse.mclib.utils.MatrixUtils.RotationOrder;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;

/**
 * The value of this container will never be null. Null values will be replaced with XYZ RotationOrder
 *
 * <p>Port of McLib 2.4.3's {@code config/values/ValueRotationOrder.java}
 * (roadmap P19). Serialized as the enum's <b>byte ordinal everywhere</b>,
 * including JSON (as an int) — never as a name.</p>
 */
public class ValueRotationOrder extends GenericValue<RotationOrder>
{
    public ValueRotationOrder(String id, RotationOrder order)
    {
        super(id, order);
    }

    @Override
    protected RotationOrder getNullValue()
    {
        return RotationOrder.XYZ;
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.value = RotationOrder.values()[buffer.readByte()];
        this.defaultValue = RotationOrder.values()[buffer.readByte()];
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeByte((byte) this.value.ordinal());
        buffer.writeByte((byte) this.defaultValue.ordinal());
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.value = RotationOrder.values()[buffer.readByte()];
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeByte((byte) this.value.ordinal());
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(RotationOrder.values()[element.getAsByte()]);
    }

    @Override
    public JsonElement valueToJSON()
    {
        return new JsonPrimitive(this.value.ordinal());
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof NbtByte)
        {
            this.set(RotationOrder.values()[((NbtByte) tag).byteValue()]);
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtByte.of((byte) this.value.ordinal());
    }

    @Override
    public ValueRotationOrder copy()
    {
        ValueRotationOrder clone = new ValueRotationOrder(this.id, this.defaultValue);
        clone.value = this.value;
        clone.serverValue = this.serverValue;

        return clone;
    }

    @Override
    public void copy(Value origin)
    {
        superCopy(origin);

        if (origin instanceof ValueRotationOrder)
        {
            this.value = ((ValueRotationOrder) origin).value;
        }
    }

    @Override
    public RotationOrder interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof RotationOrder)) return this.value;

        return factor == 1F ? (RotationOrder) to.value : this.value;
    }
}
