package mchorse.mclib.config.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import io.netty.buffer.ByteBuf;

/**
 * Port of McLib 2.4.3's {@code config/values/ValueGUI.java} (roadmap P19):
 * a value that persists nothing and only exists to contribute custom widgets
 * to the config GUI. Legacy declared {@code abstract getFields}; in the port
 * the GUI contribution comes from the S3 client-side factory registry — see
 * {@link Value} — so this class is a plain non-serializing value node.
 */
public abstract class ValueGUI extends Value
{
    public ValueGUI(String id)
    {
        super(id);
    }

    @Override
    public Object getValue()
    {
        return null;
    }

    @Override
    public void setValue(Object value)
    {}

    @Override
    public void reset()
    {}

    @Override
    public void valueFromJSON(JsonElement element)
    {}

    @Override
    public void copy(Value value)
    {}

    @Override
    public JsonElement valueToJSON()
    {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromBytes(ByteBuf buffer)
    {}

    @Override
    public void toBytes(ByteBuf buffer)
    {}
}
