package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import io.netty.buffer.ByteBuf;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.client.gui.utils.keys.KeyParser;
import mchorse.mclib.utils.ColorUtils;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueInt.java} (roadmap P19).
 * GUI widget creation ({@code getFields}) moved to the S3 client-side factory
 * registry — see {@link Value}.
 *
 * <p>MODES labels stay legacy-exact: translatable {@link IKey}s wired over
 * ByteBuf via {@link KeyParser} (the IKey family lives in <b>main</b> since S2
 * moved it behind the {@code LangKey.translator} seam).</p>
 */
public class ValueInt extends GenericNumberValue<Integer> implements IServerValue
{
    private Subtype subtype = Subtype.INTEGER;
    private List<IKey> labels;

    public ValueInt(String id)
    {
        super(id, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public ValueInt(String id, int defaultValue)
    {
        super(id, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public ValueInt(String id, int defaultValue, int min, int max)
    {
        super(id, defaultValue, min, max);
    }

    public void setColorValue(String value)
    {
        this.set(ColorUtils.parseColor(value));
    }

    @Override
    protected Integer numberToValue(Number number)
    {
        return number.intValue();
    }

    public Subtype getSubtype()
    {
        return this.subtype;
    }

    public List<IKey> getLabels()
    {
        return this.labels;
    }

    public ValueInt subtype(Subtype subtype)
    {
        this.subtype = subtype;

        return this;
    }

    public ValueInt color()
    {
        return this.subtype(Subtype.COLOR);
    }

    public ValueInt colorAlpha()
    {
        return this.subtype(Subtype.COLOR_ALPHA);
    }

    public ValueInt keybind()
    {
        return this.subtype(Subtype.KEYBIND);
    }

    public ValueInt comboKey()
    {
        return this.subtype(Subtype.COMBOKEY);
    }

    public ValueInt modes(IKey... labels)
    {
        this.labels = new ArrayList<IKey>();
        Collections.addAll(this.labels, labels);

        return this.subtype(Subtype.MODES);
    }

    public boolean isInteger()
    {
        return true;
    }

    @Override
    public void resetServer()
    {
        this.serverValue = null;
    }

    @Override
    protected Integer getNullValue()
    {
        return 0;
    }

    @Override
    public void valueFromJSON(JsonElement element)
    {
        this.set(element.getAsInt());
    }

    @Override
    public void valueFromNBT(NbtElement tag)
    {
        if (tag instanceof AbstractNbtNumber)
        {
            this.set(((AbstractNbtNumber) tag).intValue());
        }
    }

    @Override
    public NbtElement valueToNBT()
    {
        return NbtInt.of(this.value);
    }

    @Override
    public boolean parseFromCommand(String value)
    {
        try
        {
            if (this.subtype == Subtype.COLOR || this.subtype == Subtype.COLOR_ALPHA)
            {
                this.set(ColorUtils.parseColorWithException(value));
            }
            else
            {
                this.set(Integer.parseInt(value));
            }

            return true;
        }
        catch (Exception e)
        {}

        return false;
    }

    @Override
    public void copy(Value value)
    {
        if (value instanceof ValueInt)
        {
            this.set(((ValueInt) value).value);
        }
    }

    @Override
    public void copyServer(Value value)
    {
        if (value instanceof ValueInt)
        {
            this.serverValue = ((ValueInt) value).value;
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {
        superFromBytes(buffer);

        this.defaultValue = buffer.readInt();
        this.min = buffer.readInt();
        this.max = buffer.readInt();
        this.valueFromBytes(buffer);

        this.subtype = Subtype.values()[buffer.readInt()];

        if (buffer.readBoolean())
        {
            this.labels = new ArrayList<IKey>();

            for (int i = 0, c = buffer.readInt(); i < c; i++)
            {
                IKey key = KeyParser.keyFromBytes(buffer);

                if (key != null)
                {
                    this.labels.add(key);
                }
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer)
    {
        superToBytes(buffer);

        buffer.writeInt(this.defaultValue);
        buffer.writeInt(this.min);
        buffer.writeInt(this.max);
        this.valueToBytes(buffer);

        buffer.writeInt(this.subtype.ordinal());
        buffer.writeBoolean(this.labels != null);

        if (this.labels != null)
        {
            buffer.writeInt(this.labels.size());

            for (IKey key : this.labels)
            {
                KeyParser.keyToBytes(buffer, key);
            }
        }
    }

    @Override
    public void valueFromBytes(ByteBuf buffer)
    {
        this.set(buffer.readInt());
    }

    @Override
    public void valueToBytes(ByteBuf buffer)
    {
        buffer.writeInt(this.value);
    }

    @Override
    public String toString()
    {
        if (this.subtype == Subtype.COLOR || this.subtype == Subtype.COLOR_ALPHA)
        {
            return "#" + Integer.toHexString(this.value);
        }

        return Integer.toString(this.value);
    }

    @Override
    public ValueInt copy()
    {
        ValueInt clone = new ValueInt(this.id, this.defaultValue, this.min, this.max);
        clone.value = this.value;

        return clone;
    }

    public static enum Subtype
    {
        INTEGER,
        COLOR,
        COLOR_ALPHA,
        KEYBIND,
        COMBOKEY,
        MODES
    }

    public Integer interpolate(Interpolation interpolation, GenericBaseValue<?> to, float factor)
    {
        if (!(to.value instanceof Integer)) return this.value;

        return (int) interpolation.interpolate(this.value, (Integer) to.value, factor);
    }
}
